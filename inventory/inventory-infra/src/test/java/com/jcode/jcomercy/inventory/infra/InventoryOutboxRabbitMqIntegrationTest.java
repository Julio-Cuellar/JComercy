package com.jcode.jcomercy.inventory.infra;

import com.jcode.jcomercy.inventory.domain.port.InventoryOutboxPort;
import com.jcode.jcomercy.inventory.infra.outbox.InventoryOutboxDispatcher;
import com.jcode.jcomercy.inventory.infra.persistence.entity.InventoryOutboxEventJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.InventoryOutboxEventSpringDataRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase R1: el Outbox de inventario contra un RabbitMQ real. {@link InventoryOutboxIntegrationTest} usa un
 * {@code RabbitTemplate} simulado y nunca comprobó que el evento llega de verdad al broker ni con qué forma. Solo
 * corre con {@code -Prabbitmq} (necesita Docker); sin el perfil se omite.
 *
 * <p>El despachador programado está apagado en las pruebas ({@code outbox.publisher.enabled=false}), así que se crea
 * uno a mano con el {@code RabbitTemplate} real del contexto y se invoca desde la prueba.
 */
@SpringBootTest(properties = "server.port=0")
@EnabledIfSystemProperty(named = "jcomercy.test.broker", matches = "rabbitmq")
class InventoryOutboxRabbitMqIntegrationTest {

    private static final String EXCHANGE = "jcomercy.events";
    private static final String QUEUE = "jcomercy.events.inventory.pending";

    private static RabbitMQContainer broker;

    @BeforeAll
    static void startBroker() {
        broker = new RabbitMQContainer("rabbitmq:3.13-alpine");
        broker.start();
    }

    @AfterAll
    static void stopBroker() {
        if (broker != null) {
            broker.stop();
        }
    }

    @DynamicPropertySource
    static void brokerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", () -> broker.getHost());
        registry.add("spring.rabbitmq.port", () -> broker.getAmqpPort());
        registry.add("spring.rabbitmq.username", () -> broker.getAdminUsername());
        registry.add("spring.rabbitmq.password", () -> broker.getAdminPassword());
    }

    @Autowired
    private InventoryOutboxPort outboxPort;

    @Autowired
    private InventoryOutboxEventSpringDataRepository outbox;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    @DisplayName("Un evento del Outbox llega a la cola del broker real con su identificador, su clave, sus cabeceras y modo persistente")
    void anOutboxEventReachesTheRealBrokerWithItsEnvelope() {
        String companyId = "cmp_rmq_" + UUID.randomUUID().toString().substring(0, 8);
        outboxPort.publish("inventory.stock.received", companyId, "br_rmq_1", "user_rmq", "cor_rmq_1",
            Map.of("productId", "prod-1", "quantity", 50));
        InventoryOutboxEventJpaEntity event = find(companyId);
        assertThat(event.getStatus()).isEqualTo("PENDING");

        InventoryOutboxDispatcher dispatcher = new InventoryOutboxDispatcher(outbox, rabbitTemplate, EXCHANGE);
        // Otras pruebas del mismo proceso pueden haber dejado eventos pendientes delante: se despacha hasta que salga el nuestro.
        for (int round = 0; round < 5 && !"PUBLISHED".equals(find(companyId).getStatus()); round++) {
            dispatcher.publishPending();
        }
        assertThat(find(companyId).getStatus()).isEqualTo("PUBLISHED");
        assertThat(find(companyId).getPublishedAt()).isNotNull();

        Message message = receive(event.getEventId().toString());
        assertThat(message).as("el mensaje del evento %s en la cola %s", event.getEventId(), QUEUE).isNotNull();
        MessageProperties properties = message.getMessageProperties();
        assertThat(properties.getMessageId()).isEqualTo(event.getEventId().toString());
        // En un mensaje recibido el modo está en otra propiedad: getDeliveryMode() es solo para los que se envían.
        assertThat(properties.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(properties.getReceivedExchange()).isEqualTo(EXCHANGE);
        assertThat(properties.getReceivedRoutingKey()).isEqualTo("inventory.stock.received");
        assertThat(properties.getHeaders())
            .containsEntry("eventType", event.getEventType())
            .containsEntry("schemaVersion", event.getSchemaVersion())
            .containsEntry("companyId", companyId)
            .containsEntry("branchId", "br_rmq_1")
            .containsEntry("actorUserId", "user_rmq")
            .containsEntry("correlationId", "cor_rmq_1");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo(event.getPayload());
    }

    // ---- Ayudas ----

    private InventoryOutboxEventJpaEntity find(String companyId) {
        return outbox.findAll().stream()
            .filter(candidate -> companyId.equals(candidate.getCompanyId()))
            .findFirst().orElseThrow();
    }

    /** Saca mensajes de la cola hasta encontrar el del evento; los de otras pruebas se descartan. */
    private Message receive(String messageId) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            Message message = rabbitTemplate.receive(QUEUE, 1_000);
            if (message != null && messageId.equals(message.getMessageProperties().getMessageId())) {
                return message;
            }
        }
        return null;
    }
}
