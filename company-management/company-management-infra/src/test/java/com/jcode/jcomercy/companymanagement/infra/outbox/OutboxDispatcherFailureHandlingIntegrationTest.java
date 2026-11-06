package com.jcode.jcomercy.companymanagement.infra.outbox;

import com.jcode.jcomercy.companymanagement.domain.port.EventOutboxPort;
import com.jcode.jcomercy.companymanagement.infra.persistence.entity.OutboxEventJpaEntity;
import com.jcode.jcomercy.companymanagement.infra.persistence.repository.OutboxSpringDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.ConnectException;
import java.sql.Timestamp;
import java.time.Instant;
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
 * Cómo trata el despachador del Outbox cada clase de fallo (ERR-021) y en qué orden saca los eventos (BIT-038).
 *
 * <ul>
 *   <li>Un broker que no responde (caído, sin confirmar) corta el ciclo: seguir con los demás solo acumularía esperas
 *       de 5 s o más por evento y los sacaría de orden. El evento que falló cuenta el intento, sin espera propia.</li>
 *   <li>Un evento que el broker rechaza sí espera antes de reintentar, y el ciclo sigue con los demás.</li>
 *   <li>Los eventos listos (nunca fallaron, o con la espera vencida) salen del más antiguo al más nuevo.</li>
 * </ul>
 *
 * <p>No necesita Docker: un {@code RabbitTemplate} falso se comporta, evento por evento, como la prueba lo indique.
 */
@SpringBootTest(properties = "outbox.publisher.fixed-delay-ms=3600000")
class OutboxDispatcherFailureHandlingIntegrationTest {

    private static final String UNAVAILABLE = "unavailable";
    private static final String REJECTED = "rejected";

    /** Cómo responde el falso a cada evento; lo que no está aquí se confirma. */
    private static final Map<String, String> BEHAVIOR = new ConcurrentHashMap<>();

    /** Los eventos que llegaron a enviarse, en orden. */
    private static final List<String> ATTEMPTS = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class ScriptedBroker {
        @Bean
        RabbitTemplate scriptedBrokerTemplate() {
            RabbitTemplate template = mock(RabbitTemplate.class);
            doAnswer(invocation -> {
                CorrelationData correlation = invocation.getArgument(4);
                ATTEMPTS.add(correlation.getId());
                String behavior = BEHAVIOR.get(correlation.getId());
                if (UNAVAILABLE.equals(behavior)) {
                    throw new AmqpConnectException(new ConnectException("Connection refused"));
                }
                correlation.getFuture().complete(new CorrelationData.Confirm(!REJECTED.equals(behavior), REJECTED.equals(behavior) ? "nack del broker" : null));
                return null;
            }).when(template).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
            return template;
        }
    }

    @Autowired private EventOutboxPort eventOutbox;
    @Autowired private OutboxDispatcher dispatcher;
    @Autowired private OutboxSpringDataRepository outbox;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void startFromAnEmptyOutbox() {
        // La base de datos de pruebas se comparte entre clases; los eventos de otras no deben mezclarse con estos.
        outbox.deleteAll();
        BEHAVIOR.clear();
        ATTEMPTS.clear();
    }

    @Test
    @DisplayName("Si el broker no responde, el ciclo se corta: solo se intenta el primer evento y los demás quedan intactos y en orden")
    void aBrokerFailureEndsTheCycleAndKeepsTheOrder() {
        List<OutboxEventJpaEntity> events = record(3);
        BEHAVIOR.put(events.get(0).getEventId(), UNAVAILABLE);

        dispatcher.publishPending();

        assertThat(ATTEMPTS).as("eventos que llegaron a enviarse con el broker caído").containsExactly(events.get(0).getEventId());
        OutboxEventJpaEntity first = reload(events.get(0));
        assertThat(first.getStatus()).isEqualTo("PENDING");
        assertThat(first.getRetryCount()).as("intentos del evento que falló").isEqualTo(1);
        assertThat(first.getErrorMessage()).isNotBlank();
        assertThat(first.getNextRetryAt()).as("un fallo del broker no le da al evento una espera propia: se reintenta en el siguiente ciclo").isNull();
        for (OutboxEventJpaEntity untouched : events.subList(1, 3)) {
            OutboxEventJpaEntity stored = reload(untouched);
            assertThat(stored.getStatus()).isEqualTo("PENDING");
            assertThat(stored.getRetryCount()).as("intentos de un evento que ni se intentó").isZero();
        }

        BEHAVIOR.clear();
        ATTEMPTS.clear();
        dispatcher.publishPending();

        assertThat(ATTEMPTS).as("al volver el broker, salen en el orden en que se crearon")
            .containsExactlyElementsOf(events.stream().map(OutboxEventJpaEntity::getEventId).toList());
        events.forEach(event -> assertThat(reload(event).getStatus()).isEqualTo("PUBLISHED"));
    }

    @Test
    @DisplayName("Un evento que el broker rechaza espera antes de reintentar y no detiene a los demás")
    void aRejectedEventWaitsAndTheCycleContinues() {
        List<OutboxEventJpaEntity> events = record(3);
        BEHAVIOR.put(events.get(0).getEventId(), REJECTED);

        dispatcher.publishPending();

        assertThat(ATTEMPTS).as("los tres se intentan").hasSize(3);
        OutboxEventJpaEntity rejected = reload(events.get(0));
        assertThat(rejected.getStatus()).isEqualTo("PENDING");
        assertThat(rejected.getRetryCount()).isEqualTo(1);
        assertThat(rejected.getNextRetryAt()).as("espera antes del siguiente intento").isAfter(Instant.now());
        assertThat(reload(events.get(1)).getStatus()).isEqualTo("PUBLISHED");
        assertThat(reload(events.get(2)).getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("Los eventos listos salen del más antiguo al más nuevo, aunque el antiguo sea un reintento vencido")
    void readyEventsGoOutOldestFirst() {
        List<OutboxEventJpaEntity> events = record(2);
        // El primero ya falló una vez y su espera venció; el segundo nunca se intentó.
        jdbc.update("UPDATE company_management.outbox_event SET retry_count = 1, next_retry_at = ? WHERE event_id = ?",
            Timestamp.from(Instant.now().minusSeconds(60)), events.get(0).getEventId());

        dispatcher.publishPending();

        assertThat(ATTEMPTS).as("orden en que salieron")
            .containsExactlyElementsOf(events.stream().map(OutboxEventJpaEntity::getEventId).toList());
    }

    private List<OutboxEventJpaEntity> record(int count) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < count; i++) {
            eventOutbox.recordEvent("account.created", "1.0", 1L, "company_management", "cor_fh_" + i, null,
                "acc_fh_" + suffix + "_" + i, null, null, "usr_fh", Map.of("n", i));
        }
        List<OutboxEventJpaEntity> events = outbox.findAll().stream()
            .sorted(Comparator.comparing(OutboxEventJpaEntity::getCreatedAt))
            .toList();
        assertThat(events).hasSize(count);
        return events;
    }

    private OutboxEventJpaEntity reload(OutboxEventJpaEntity event) {
        return outbox.findById(event.getId()).orElseThrow();
    }
}
