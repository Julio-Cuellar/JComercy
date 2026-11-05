package com.jcode.jcomercy.inventory.infra;

import com.jcode.jcomercy.inventory.domain.port.InventoryOutboxPort;
import com.jcode.jcomercy.inventory.infra.outbox.InventoryOutboxDispatcher;
import com.jcode.jcomercy.inventory.infra.persistence.entity.InventoryOutboxEventJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.InventoryOutboxEventSpringDataRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Fase R3: qué retiene el despachador del Outbox de inventario mientras espera al broker. Publicar cada evento espera
 * hasta 5 s su confirmación, y un ciclo procesa hasta 50: con un broker lento o colgado, si todo el ciclo va dentro de
 * una sola transacción, la conexión de la base de datos queda retenida durante minutos y los eventos ya confirmados no
 * se ven como publicados hasta el final (si el proceso cae a mitad del ciclo, se volverían a publicar).
 *
 * <p>No necesita Docker: un {@code RabbitTemplate} falso confirma todo al instante, salvo un evento que se deja
 * esperando, como lo haría un broker que no responde. El despachador programado está apagado en las pruebas; aquí se
 * enciende, con el ciclo diferido una hora, para probar el componente real con su transacción.
 */
@SpringBootTest(properties = {"server.port=0", "outbox.publisher.enabled=true", "outbox.publisher.fixed-delay-ms=3600000"})
class InventoryOutboxDispatcherConnectionIntegrationTest {

    private static volatile String blockedEvent;
    private static volatile String silentEvent;
    private static volatile CountDownLatch reached = new CountDownLatch(1);
    private static volatile CountDownLatch release = new CountDownLatch(1);

    /**
     * Confirma al instante todo evento, menos dos que la prueba puede elegir: uno que deja esperando hasta soltarlo y
     * otro que nunca confirma.
     */
    @TestConfiguration
    static class SlowBroker {
        @Bean
        RabbitTemplate slowBrokerTemplate() {
            RabbitTemplate template = mock(RabbitTemplate.class);
            doAnswer(invocation -> {
                CorrelationData correlation = invocation.getArgument(4);
                if (correlation.getId() != null && correlation.getId().equals(silentEvent)) {
                    return null; // el broker recibe el evento y nunca lo confirma
                }
                if (correlation.getId() != null && correlation.getId().equals(blockedEvent)) {
                    reached.countDown();
                    release.await(30, TimeUnit.SECONDS);
                }
                correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
                return null;
            }).when(template).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
            return template;
        }
    }

    @Autowired
    private InventoryOutboxPort outboxPort;

    @Autowired
    private InventoryOutboxDispatcher dispatcher;

    @Autowired
    private InventoryOutboxEventSpringDataRepository outbox;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Mientras espera la respuesta del broker, el despachador no retiene una transacción ni una conexión de la base de datos")
    void whileWaitingForTheBrokerTheDispatcherHoldsNoTransactionNorConnection() throws Exception {
        dispatcher.publishPending(); // deja al día lo que otras pruebas hayan dejado pendiente

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 3; i++) {
            outboxPort.publish("inventory.stock.received", "cmp_slow_" + suffix + "_" + i, "br_slow_1", "user_slow",
                "cor_slow_" + i, Map.of("quantity", i + 1));
        }
        List<InventoryOutboxEventJpaEntity> events = outbox.findAll().stream()
            .filter(event -> event.getCompanyId().startsWith("cmp_slow_" + suffix))
            .sorted(Comparator.comparing(InventoryOutboxEventJpaEntity::getCreatedAt))
            .toList();
        assertThat(events).hasSize(3);

        blockedEvent = events.get(2).getEventId().toString();
        reached = new CountDownLatch(1);
        release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> cycle = pool.submit(dispatcher::publishPending);
            boolean waiting = reached.await(20, TimeUnit.SECONDS);
            // Se mide mientras el tercer evento espera al broker. Primero las conexiones, para no contar la lectura de la prueba.
            int heldConnections = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();
            String first = statusOf(events.get(0));
            String second = statusOf(events.get(1));
            release.countDown();
            cycle.get(30, TimeUnit.SECONDS);

            assertThat(waiting).as("el despachador llegó al evento que espera al broker").isTrue();
            SoftAssertions.assertSoftly(soft -> {
                soft.assertThat(first).as("primer evento, ya confirmado mientras el tercero espera").isEqualTo("PUBLISHED");
                soft.assertThat(second).as("segundo evento, ya confirmado mientras el tercero espera").isEqualTo("PUBLISHED");
                soft.assertThat(heldConnections).as("conexiones de la base de datos retenidas mientras espera al broker").isZero();
                soft.assertThat(statusOf(events.get(2))).as("tercer evento, una vez que el broker responde").isEqualTo("PUBLISHED");
            });
        } finally {
            release.countDown();
            blockedEvent = null;
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Un evento que el broker no confirma queda pendiente con un error legible, no vacío")
    void anEventTheBrokerNeverConfirmsKeepsAReadableError() {
        dispatcher.publishPending(); // deja al día lo que otras pruebas hayan dejado pendiente
        String companyId = "cmp_silent_" + UUID.randomUUID().toString().substring(0, 8);
        outboxPort.publish("inventory.stock.received", companyId, "br_slow_1", "user_slow", "cor_silent", Map.of("quantity", 1));
        InventoryOutboxEventJpaEntity event = outbox.findAll().stream()
            .filter(candidate -> companyId.equals(candidate.getCompanyId()))
            .findFirst().orElseThrow();

        silentEvent = event.getEventId().toString();
        try {
            dispatcher.publishPending(); // espera 5 s una confirmación que no llega
        } finally {
            silentEvent = null;
        }

        InventoryOutboxEventJpaEntity stored = outbox.findById(event.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo("PENDING");
        assertThat(stored.getRetryCount()).isGreaterThanOrEqualTo(1);
        assertThat(stored.getLastError()).as("error guardado de un evento que el broker no confirmó").isNotBlank();
    }

    private String statusOf(InventoryOutboxEventJpaEntity event) {
        return outbox.findById(event.getId()).orElseThrow().getStatus();
    }
}
