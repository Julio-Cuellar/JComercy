package com.jcode.jcomercy.sales.infra.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.sales.application.port.SaleEventsPort;
import com.jcode.jcomercy.sales.domain.model.sale.Payment;
import com.jcode.jcomercy.sales.domain.model.sale.PaymentMethod;
import com.jcode.jcomercy.sales.domain.model.sale.Sale;
import com.jcode.jcomercy.sales.domain.model.sale.SaleChannel;
import com.jcode.jcomercy.sales.domain.model.sale.SaleLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * El despachador del Outbox de ventas, con un {@code RabbitTemplate} simulado que aplica el mismo post-procesador y
 * guarda lo que habría salido hacia el broker. Cubre lo que inventario aprendió con sus errores: no retiene una
 * transacción esperando al broker, corta el ciclo si el broker no responde y deja esperar a un evento que el broker
 * rechaza sin tapar a los demás.
 */
@SpringBootTest
class SaleOutboxDispatcherIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-07T18:00:00Z");

    private record Sent(String routingKey, Message message) { }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private SaleEventsPort events;
    @Autowired private ObjectMapper mapper;

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private String company;

    @BeforeEach
    void aCleanOutbox() {
        // La base de datos de pruebas se comparte entre clases: se parte de una tabla vacía (como en inventario, ERR-022).
        jdbc.update("DELETE FROM sales.outbox_event");
        sent.clear();
        company = "cmp_dsp_" + UUID.randomUUID();
    }

    @Test
    @DisplayName("Publica los eventos pendientes en orden, con el sobre del contrato, sus cabeceras y modo persistente")
    void itPublishesPendingEventsInOrderWithTheEnvelope() throws Exception {
        Sale sale = completedSale();
        events.completed(sale, "cor_1");
        events.cancelled(sale.cancel("El cliente devolvió todo", NOW.plusSeconds(60)), "usr_gerente", "cor_2");
        var dispatcher = new SaleOutboxDispatcher(jdbc, template(Set.of(), null), "jcomercy.events");

        dispatcher.publishPending();

        assertThat(sent).extracting(Sent::routingKey).containsExactly("sales.sale.completed", "sales.sale.cancelled");
        List<Map<String, Object>> rows = rows();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("STATUS")).isEqualTo("PUBLISHED");
            assertThat(row.get("PUBLISHED_AT")).isNotNull();
            assertThat(row.get("NEXT_RETRY_AT")).isNull();
        });
        Sent first = sent.get(0);
        MessageProperties properties = first.message().getMessageProperties();
        assertThat(properties.getMessageId()).isEqualTo(rows.get(0).get("EVENT_ID").toString());
        assertThat(properties.getContentType()).isEqualTo("application/json");
        assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(properties.getHeaders()).containsEntry("eventType", "sales.sale.completed").containsEntry("schemaVersion", "1.0")
            .containsEntry("companyId", company).containsEntry("branchId", "br_1").containsEntry("actorUserId", "usr_cajera")
            .containsEntry("correlationId", "cor_1");
        JsonNode body = mapper.readTree(new String(first.message().getBody(), StandardCharsets.UTF_8));
        assertThat(body.fieldNames()).toIterable().containsExactly("eventId", "eventType", "schemaVersion", "aggregateVersion",
            "sequence", "occurredAt", "producerModule", "correlationId", "causationId", "accountId", "companyId", "branchId",
            "actorUserId", "data");
        assertThat(body.path("producerModule").asText()).isEqualTo("sales");
        assertThat(body.path("sequence").asLong()).isEqualTo(((Number) rows.get(0).get("SEQUENCE")).longValue());
        assertThat(body.path("aggregateVersion").isNull()).isTrue();
        assertThat(body.path("accountId").isNull()).isTrue();
        assertThat(body.path("data")).isEqualTo(mapper.readTree((String) rows.get(0).get("PAYLOAD")));
    }

    @Test
    @DisplayName("Un evento que el broker rechaza espera para reintentarse y no tapa a los demás")
    void aRejectedEventWaitsAndDoesNotBlockTheOthers() {
        Sale sale = completedSale();
        events.completed(sale, "cor_1");
        events.cancelled(sale.cancel("x", NOW.plusSeconds(60)), "usr_gerente", "cor_2");
        var dispatcher = new SaleOutboxDispatcher(jdbc, template(Set.of("sales.sale.completed"), null), "jcomercy.events");

        dispatcher.publishPending();

        assertThat(sent).extracting(Sent::routingKey).containsExactly("sales.sale.completed", "sales.sale.cancelled");
        Map<String, Object> rejected = rows().get(0);
        assertThat(rejected.get("STATUS")).isEqualTo("PENDING");
        assertThat(((Number) rejected.get("RETRY_COUNT")).intValue()).isEqualTo(1);
        assertThat((String) rejected.get("LAST_ERROR")).contains("rechazó");
        assertThat(rejected.get("NEXT_RETRY_AT")).as("espera antes de reintentar").isNotNull();
        assertThat(rows().get(1).get("STATUS")).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("Si el broker no responde, se corta el ciclo: el primer evento cuenta el intento y los demás quedan intactos")
    void anUnavailableBrokerCutsTheCycle() {
        Sale sale = completedSale();
        events.completed(sale, "cor_1");
        events.cancelled(sale.cancel("x", NOW.plusSeconds(60)), "usr_gerente", "cor_2");
        var dispatcher = new SaleOutboxDispatcher(jdbc, template(Set.of(), new AmqpConnectException(new IOException("rechazada"))),
            "jcomercy.events");

        dispatcher.publishPending();

        List<Map<String, Object>> rows = rows();
        assertThat(rows.get(0).get("STATUS")).isEqualTo("PENDING");
        assertThat(((Number) rows.get(0).get("RETRY_COUNT")).intValue()).isEqualTo(1);
        assertThat(rows.get(0).get("LAST_ERROR")).isNotNull();
        assertThat(rows.get(0).get("NEXT_RETRY_AT")).as("sin espera propia: el ciclo ya se cortó").isNull();
        assertThat(rows.get(1).get("STATUS")).isEqualTo("PENDING");
        assertThat(((Number) rows.get(1).get("RETRY_COUNT")).intValue()).isZero();
    }

    @Test
    @DisplayName("Un evento que espera su reintento no se envía hasta que llega su hora")
    void aWaitingEventIsNotRetriedBeforeItsTime() {
        events.completed(completedSale(), "cor_1");
        jdbc.update("UPDATE sales.outbox_event SET next_retry_at = ?", java.sql.Timestamp.from(Instant.now().plusSeconds(3600)));
        var dispatcher = new SaleOutboxDispatcher(jdbc, template(Set.of(), null), "jcomercy.events");

        dispatcher.publishPending();

        assertThat(sent).isEmpty();
        jdbc.update("UPDATE sales.outbox_event SET next_retry_at = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)));

        dispatcher.publishPending();

        assertThat(sent).hasSize(1);
        assertThat(rows().get(0).get("STATUS")).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("Sin broker configurado no hace nada y deja los eventos pendientes")
    void withoutABrokerItDoesNothing() {
        events.completed(completedSale(), "cor_1");

        new SaleOutboxDispatcher(jdbc, null, "jcomercy.events").publishPending();

        assertThat(rows().get(0).get("STATUS")).isEqualTo("PENDING");
        assertThat(((Number) rows().get(0).get("RETRY_COUNT")).intValue()).isZero();
    }

    // ---- Ayudas ----

    private List<Map<String, Object>> rows() {
        return jdbc.queryForList("SELECT * FROM sales.outbox_event WHERE company_id = ? ORDER BY sequence", company);
    }

    private Sale completedSale() {
        return Sale.create(UUID.randomUUID(), company, "br_1", UUID.randomUUID(), SaleChannel.MOSTRADOR, null, "MXN",
                List.of(SaleLine.of(UUID.randomUUID(), "CAFE", "Café", new BigDecimal("2"), new BigDecimal("45.50"))), "usr_cajera", NOW)
            .registerPayments(List.of(new Payment(PaymentMethod.CASH, new BigDecimal("91.00"), null)))
            .complete("TCK-000001", NOW.plusSeconds(30));
    }

    /** Un RabbitTemplate simulado: confirma lo que envía, salvo las claves que rechaza o la falla que se le provoque. */
    private RabbitTemplate template(Set<String> rejectedRoutingKeys, RuntimeException failure) {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            if (failure != null) {
                throw failure;
            }
            String routingKey = invocation.getArgument(1);
            MessagePostProcessor processor = invocation.getArgument(3);
            CorrelationData correlation = invocation.getArgument(4);
            Message message = new SimpleMessageConverter().toMessage(invocation.getArgument(2), new MessageProperties());
            sent.add(new Sent(routingKey, processor.postProcessMessage(message)));
            boolean rejected = rejectedRoutingKeys.contains(routingKey);
            correlation.getFuture().complete(new CorrelationData.Confirm(!rejected, rejected ? "el broker rechazó el mensaje" : null));
            return null;
        }).when(template).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
        return template;
    }
}
