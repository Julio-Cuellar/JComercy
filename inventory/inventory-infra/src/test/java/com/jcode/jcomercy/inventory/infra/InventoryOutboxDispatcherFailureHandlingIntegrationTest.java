package com.jcode.jcomercy.inventory.infra;

import com.jcode.jcomercy.inventory.domain.port.InventoryOutboxPort;
import com.jcode.jcomercy.inventory.infra.outbox.InventoryOutboxDispatcher;
import com.jcode.jcomercy.inventory.infra.persistence.entity.InventoryOutboxEventJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.InventoryOutboxEventSpringDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.ConnectException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Cómo trata el despachador del Outbox de inventario cada clase de fallo (ERR-021 y ERR-022):
 *
 * <ul>
 *   <li>Un broker que no responde corta el ciclo; el evento que falló cuenta el intento y los demás quedan intactos.</li>
 *   <li>Un evento que el broker rechaza espera antes de reintentar: 50 eventos que siempre fallan no deben tapar a los
 *       que vienen detrás, como pasaba cuando cada ciclo reintentaba los 50 más antiguos.</li>
 * </ul>
 *
 * <p>No necesita Docker: un {@code RabbitTemplate} falso se comporta, evento por evento, como la prueba lo indique.
 */
@SpringBootTest(properties = "server.port=0")
class InventoryOutboxDispatcherFailureHandlingIntegrationTest {

    private static final String EXCHANGE = "jcomercy.events";
    private static final String UNAVAILABLE = "unavailable";
    private static final String REJECTED = "rejected";

    private final Map<String, String> behavior = new ConcurrentHashMap<>();
    private final List<String> attempts = new CopyOnWriteArrayList<>();

    @Autowired private InventoryOutboxPort outboxPort;
    @Autowired private InventoryOutboxEventSpringDataRepository outbox;

    private InventoryOutboxDispatcher dispatcher;

    @BeforeEach
    void startFromAnEmptyOutbox() {
        // La base de datos de pruebas se comparte entre clases; los eventos de otras no deben mezclarse con estos.
        outbox.deleteAll();
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            attempts.add(correlation.getId());
            String how = behavior.get(correlation.getId());
            if (UNAVAILABLE.equals(how)) {
                throw new AmqpConnectException(new ConnectException("Connection refused"));
            }
            boolean rejected = REJECTED.equals(how);
            correlation.getFuture().complete(new CorrelationData.Confirm(!rejected, rejected ? "nack del broker" : null));
            return null;
        }).when(template).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
        dispatcher = new InventoryOutboxDispatcher(outbox, template, EXCHANGE);
    }

    @Test
    @DisplayName("Si el broker no responde, el ciclo se corta: solo se intenta el primer evento y los demás quedan intactos y en orden")
    void aBrokerFailureEndsTheCycleAndKeepsTheOrder() {
        List<InventoryOutboxEventJpaEntity> events = record(3);
        behavior.put(id(events.get(0)), UNAVAILABLE);

        dispatcher.publishPending();

        assertThat(attempts).as("eventos que llegaron a enviarse con el broker caído").containsExactly(id(events.get(0)));
        InventoryOutboxEventJpaEntity first = reload(events.get(0));
        assertThat(first.getStatus()).isEqualTo("PENDING");
        assertThat(first.getRetryCount()).as("intentos del evento que falló").isEqualTo(1);
        assertThat(first.getLastError()).isNotBlank();
        for (InventoryOutboxEventJpaEntity untouched : events.subList(1, 3)) {
            assertThat(reload(untouched).getStatus()).isEqualTo("PENDING");
            assertThat(reload(untouched).getRetryCount()).as("intentos de un evento que ni se intentó").isZero();
        }

        behavior.clear();
        attempts.clear();
        dispatcher.publishPending();

        assertThat(attempts).as("al volver el broker, salen en el orden en que se crearon")
            .containsExactlyElementsOf(events.stream().map(this::id).toList());
        events.forEach(event -> assertThat(reload(event).getStatus()).isEqualTo("PUBLISHED"));
    }

    @Test
    @DisplayName("Cincuenta eventos que el broker siempre rechaza no tapan al que viene detrás")
    void fiftyEventsThatAlwaysFailDoNotBlockTheNextOne() {
        List<InventoryOutboxEventJpaEntity> events = record(51);
        events.subList(0, 50).forEach(event -> behavior.put(id(event), REJECTED));
        InventoryOutboxEventJpaEntity last = events.get(50);

        dispatcher.publishPending(); // intenta los 50 más antiguos, y todos fallan
        assertThat(attempts).as("primer ciclo: los 50 más antiguos").hasSize(50).doesNotContain(id(last));

        attempts.clear();
        dispatcher.publishPending(); // los 50 esperan su reintento: ahora le toca al que quedó fuera

        assertThat(attempts).as("segundo ciclo: el evento 51 se intenta").contains(id(last));
        assertThat(reload(last).getStatus()).isEqualTo("PUBLISHED");
    }

    private List<InventoryOutboxEventJpaEntity> record(int count) {
        String company = "cmp_fh_" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < count; i++) {
            outboxPort.publish("inventory.stock.received", company, "br_fh", "user_fh", "cor_fh_" + i, Map.of("n", i));
        }
        List<InventoryOutboxEventJpaEntity> events = outbox.findAll().stream()
            .sorted(Comparator.comparing(InventoryOutboxEventJpaEntity::getCreatedAt))
            .toList();
        assertThat(events).hasSize(count);
        return events;
    }

    private String id(InventoryOutboxEventJpaEntity event) {
        return event.getEventId().toString();
    }

    private InventoryOutboxEventJpaEntity reload(InventoryOutboxEventJpaEntity event) {
        return outbox.findById(event.getId()).orElseThrow();
    }
}
