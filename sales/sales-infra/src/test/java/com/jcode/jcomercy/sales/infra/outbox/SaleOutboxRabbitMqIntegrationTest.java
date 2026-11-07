package com.jcode.jcomercy.sales.infra.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.sales.application.port.SaleEventsPort;
import com.jcode.jcomercy.sales.domain.model.sale.Payment;
import com.jcode.jcomercy.sales.domain.model.sale.PaymentMethod;
import com.jcode.jcomercy.sales.domain.model.sale.Sale;
import com.jcode.jcomercy.sales.domain.model.sale.SaleChannel;
import com.jcode.jcomercy.sales.domain.model.sale.SaleLine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El Outbox de ventas contra un RabbitMQ real: el evento llega a la cola {@code jcomercy.events.sales.pending}, con su
 * identificador, su clave de enrutamiento, sus cabeceras, modo persistente y el sobre del contrato. Solo corre con
 * {@code -Prabbitmq} (necesita Docker); sin el perfil se omite. El despachador programado está apagado en las pruebas,
 * así que se crea uno a mano con el {@code RabbitTemplate} real del contexto.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "jcomercy.test.broker", matches = "rabbitmq")
class SaleOutboxRabbitMqIntegrationTest {

    private static final String EXCHANGE = "jcomercy.events";
    private static final String QUEUE = "jcomercy.events.sales.pending";
    private static final Instant NOW = Instant.parse("2026-11-07T18:00:00Z");

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

    @Autowired private SaleEventsPort events;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private ObjectMapper mapper;

    @BeforeEach
    void aCleanOutbox() {
        jdbc.update("DELETE FROM sales.outbox_event");
    }

    @Test
    @DisplayName("Un evento de venta llega a la cola del broker real con su identificador, su clave, sus cabeceras y modo persistente")
    void aSaleEventReachesTheRealBrokerWithItsEnvelope() throws Exception {
        String company = "cmp_rmq_" + UUID.randomUUID().toString().substring(0, 8);
        Sale sale = Sale.create(UUID.randomUUID(), company, "br_rmq_1", UUID.randomUUID(), SaleChannel.MOSTRADOR, null, "MXN",
                List.of(SaleLine.of(UUID.randomUUID(), "CAFE", "Café", new BigDecimal("2"), new BigDecimal("45.50"))), "usr_rmq", NOW)
            .registerPayments(List.of(new Payment(PaymentMethod.CASH, new BigDecimal("91.00"), null)))
            .complete("TCK-000001", NOW.plusSeconds(30));
        events.completed(sale, "cor_rmq_1");
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM sales.outbox_event WHERE company_id = ?", company);
        assertThat(row.get("STATUS")).isEqualTo("PENDING");

        new SaleOutboxDispatcher(jdbc, rabbitTemplate, EXCHANGE).publishPending();

        assertThat(jdbc.queryForMap("SELECT * FROM sales.outbox_event WHERE company_id = ?", company).get("STATUS")).isEqualTo("PUBLISHED");
        Message message = receive(row.get("EVENT_ID").toString());
        assertThat(message).as("el mensaje del evento %s en la cola %s", row.get("EVENT_ID"), QUEUE).isNotNull();
        var properties = message.getMessageProperties();
        assertThat(properties.getMessageId()).isEqualTo(row.get("EVENT_ID").toString());
        assertThat(properties.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(properties.getReceivedExchange()).isEqualTo(EXCHANGE);
        assertThat(properties.getReceivedRoutingKey()).isEqualTo("sales.sale.completed");
        assertThat(properties.getContentType()).isEqualTo("application/json");
        assertThat(properties.getHeaders()).containsEntry("eventType", "sales.sale.completed").containsEntry("companyId", company)
            .containsEntry("branchId", "br_rmq_1").containsEntry("actorUserId", "usr_rmq").containsEntry("correlationId", "cor_rmq_1");
        JsonNode body = mapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
        assertThat(body.path("producerModule").asText()).isEqualTo("sales");
        assertThat(body.path("data").path("folio").asText()).isEqualTo("TCK-000001");
    }

    /** El mensaje de la cola con ese identificador; otros mensajes que haya delante se descartan. */
    private Message receive(String eventId) {
        for (int i = 0; i < 20; i++) {
            Message message = rabbitTemplate.receive(QUEUE, 1000);
            if (message != null && eventId.equals(message.getMessageProperties().getMessageId())) {
                return message;
            }
        }
        return null;
    }
}
