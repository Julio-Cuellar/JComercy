package com.jcode.jcomercy.sales.infra.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jcode.jcomercy.sales.infra.SalesTokens;
import com.jcode.jcomercy.sales.infra.inventory.FakeInventoryServer;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lo que el Outbox de ventas publica, contra {@code asyncapi-sales-v1.yaml}. Como las de inventario y company-management,
 * solo se exige en un sentido: cada evento que el módulo emite de verdad debe estar declarado y publicarse como el contrato
 * dice (sobre de 14 campos, tipos, {@code data} con campos declarados y estados del enum). No necesita Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SaleOutboxEventsContractIntegrationTest {

    private static final String CONTRACT = "asyncapi-sales-v1.yaml";
    private static final Set<String> EMITTED = Set.of("sales.sale.completed", "sales.sale.cancelled");
    private static final FakeInventoryServer INVENTORY;

    static {
        try {
            INVENTORY = FakeInventoryServer.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void inventoryUrl(DynamicPropertyRegistry registry) {
        registry.add("sales.inventory.base-url", INVENTORY::baseUrl);
        registry.add("sales.inventory.read-timeout-ms", () -> "2000");
    }

    @AfterAll
    static void stopInventory() {
        INVENTORY.close();
    }

    private record Sent(String routingKey, Message message) { }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("Cada evento que ventas emite está declarado en el AsyncAPI y se publica como el sobre que el contrato exige")
    @SuppressWarnings("unchecked")
    void everyEmittedEventIsDeclaredAndPublishedAsTheContractRequires() throws Exception {
        jdbc.update("DELETE FROM sales.outbox_event");
        String company = "cmp_ctr_" + UUID.randomUUID().toString().substring(0, 8);
        String seller = SalesTokens.bearer("usr_cajera", company, List.of("br_1"), "sales.create sales.read");
        String manager = SalesTokens.bearer("usr_gerente", company, List.of("br_1"), "sales.cancel sales.read sales.prices.write");
        UUID cafe = UUID.randomUUID();
        INVENTORY.product(cafe, company, "CAFE", "Café", "10");
        mockMvc.perform(put("/api/v1/companies/{c}/products/{p}/price", company, cafe).header("Authorization", manager)
            .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":45.50,\"currency\":\"MXN\"}")).andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/sales", company, "br_1").header("Authorization", seller)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseId\":\"" + UUID.randomUUID() + "\",\"lines\":[{\"productId\":\"" + cafe
                    + "\",\"quantity\":2}],\"payments\":[{\"method\":\"CASH\",\"amount\":100}]}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String saleId = JsonPath.read(body, "$.saleId");
        mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/sales/{s}/cancellation", company, "br_1", saleId)
                .header("Authorization", manager).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Prueba de contrato\"}"))
            .andExpect(status().isOk());

        var dispatcher = new SaleOutboxDispatcher(jdbc, capturingTemplate(), "jcomercy.events");
        dispatcher.publishPending();

        Map<String, Object> contract = contract();
        Map<String, Object> envelope = (Map<String, Object>) schemas(contract).get("EventEnvelope");
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(sent).as("eventos publicados").hasSize(2);
        soft.assertThat(sent.stream().map(Sent::routingKey).toList()).as("tipos de evento que salieron, en orden")
            .containsExactly("sales.sale.completed", "sales.sale.cancelled");
        soft.assertThat(EMITTED).as("los dos eventos que ventas emite están declarados").allSatisfy(type ->
            assertThat(((Map<String, Object>) contract.get("channels"))).containsKey("events/" + type.replace('.', '/')));
        for (Sent one : sent) {
            check(soft, contract, envelope, one);
        }
        // «sequence» crece con el orden en que se registran los eventos.
        List<Long> sequences = new ArrayList<>();
        sent.forEach(one -> sequences.add(bodyOf(one).path("sequence").asLong(-1)));
        soft.assertThat(sequences.get(1)).as("sequence del segundo evento frente al primero (%s)", sequences).isGreaterThan(sequences.get(0));
        soft.assertAll();
    }

    // ---- Contraste con el contrato ----

    @SuppressWarnings("unchecked")
    private void check(SoftAssertions soft, Map<String, Object> contract, Map<String, Object> envelope, Sent one) {
        String type = one.routingKey();
        MessageProperties properties = one.message().getMessageProperties();

        Map<String, Object> channel = (Map<String, Object>) ((Map<String, Object>) contract.get("channels")).get("events/" + type.replace('.', '/'));
        soft.assertThat(channel).as("%s: canal declarado en el contrato", type).isNotNull();
        if (channel == null) {
            return;
        }
        Map<String, Object> message = resolve(contract, ((Map<String, Object>) channel.get("publish")).get("message"));
        String expectedContentType = String.valueOf(message.getOrDefault("contentType", contract.get("defaultContentType")));
        soft.assertThat(properties.getContentType()).as("%s: tipo de contenido", type).isEqualTo(expectedContentType);

        JsonNode body = bodyOf(one);
        Map<String, Object> envelopeProperties = (Map<String, Object>) envelope.get("properties");
        for (String field : (List<String>) envelope.get("required")) {
            Map<String, Object> fieldSchema = (Map<String, Object>) envelopeProperties.get(field);
            soft.assertThat(body.has(field)).as("%s: campo obligatorio «%s» en el cuerpo", type, field).isTrue();
            if (Boolean.TRUE.equals(fieldSchema.get("nullable")) && body.has(field) && body.get(field).isNull()) {
                continue;
            }
            soft.assertThat(body.hasNonNull(field)).as("%s: «%s» no es nulo", type, field).isTrue();
            if (body.hasNonNull(field)) {
                soft.assertThat(typeOf(body.get(field))).as("%s: tipo de «%s»", type, field).isEqualTo(String.valueOf(fieldSchema.get("type")));
                if (body.get(field).isTextual()) {
                    soft.assertThat(body.get(field).asText()).as("%s: «%s» con contenido", type, field).isNotBlank();
                }
            }
        }
        if (body.hasNonNull("occurredAt")) {
            soft.assertThat(isInstant(body.get("occurredAt").asText())).as("%s: occurredAt es una fecha y hora", type).isTrue();
        }
        soft.assertThat(body.path("producerModule").asText()).as("%s: producerModule", type).isEqualTo("sales");
        soft.assertThat(body.path("eventId").asText()).as("%s: eventId del cuerpo y messageId", type).isEqualTo(properties.getMessageId());
        soft.assertThat(body.path("eventType").asText()).as("%s: eventType del cuerpo y clave de enrutamiento", type).isEqualTo(type);
        soft.assertThat(body.path("schemaVersion").asText()).as("%s: schemaVersion del cuerpo y de la cabecera", type)
            .isEqualTo(String.valueOf(properties.getHeaders().get("schemaVersion")));

        Map<String, Object> dataSchema = dataSchemaOf(contract, message);
        soft.assertThat(dataSchema).as("%s: esquema del data en el contrato", type).isNotNull();
        if (dataSchema == null) {
            return;
        }
        Map<String, Object> dataProperties = (Map<String, Object>) dataSchema.get("properties");
        List<String> undeclared = new ArrayList<>();
        body.path("data").fieldNames().forEachRemaining(name -> {
            if (!dataProperties.containsKey(name)) {
                undeclared.add(name);
            }
        });
        soft.assertThat(undeclared).as("%s: campos del data que el contrato no declara", type).isEmpty();
        for (String declared : List.of("saleId", "folio", "channel", "status", "warehouseId", "currency", "total", "lines")) {
            soft.assertThat(body.path("data").has(declared)).as("%s: el data trae «%s»", type, declared).isTrue();
        }
        for (String enumerated : List.of("channel", "status")) {
            Map<String, Object> field = (Map<String, Object>) dataProperties.get(enumerated);
            if (field != null && field.get("enum") != null && body.path("data").hasNonNull(enumerated)) {
                soft.assertThat((List<String>) field.get("enum")).as("%s: «%s» que declara el contrato", type, enumerated)
                    .contains(body.path("data").get(enumerated).asText());
            }
        }
        Map<String, Object> lineSchema = (Map<String, Object>) schemas(contract).get("SaleLineData");
        Map<String, Object> lineProperties = (Map<String, Object>) lineSchema.get("properties");
        for (JsonNode line : body.path("data").path("lines")) {
            List<String> undeclaredLine = new ArrayList<>();
            line.fieldNames().forEachRemaining(name -> {
                if (!lineProperties.containsKey(name)) {
                    undeclaredLine.add(name);
                }
            });
            soft.assertThat(undeclaredLine).as("%s: campos de una línea que el contrato no declara", type).isEmpty();
        }
        if ("sales.sale.cancelled".equals(type)) {
            soft.assertThat(body.path("data").path("reason").asText()).as("%s: motivo de la cancelación", type).isEqualTo("Prueba de contrato");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataSchemaOf(Map<String, Object> contract, Map<String, Object> message) {
        Map<String, Object> payload = resolve(contract, message.get("payload"));
        List<Map<String, Object>> parts = (List<Map<String, Object>>) payload.get("allOf");
        if (parts == null) {
            return null;
        }
        for (Map<String, Object> part : parts) {
            Map<String, Object> properties = (Map<String, Object>) part.get("properties");
            if (properties != null && properties.get("data") != null) {
                return resolve(contract, properties.get("data"));
            }
        }
        return null;
    }

    // ---- Ayudas ----

    private RabbitTemplate capturingTemplate() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            String routingKey = invocation.getArgument(1);
            MessagePostProcessor processor = invocation.getArgument(3);
            CorrelationData correlation = invocation.getArgument(4);
            // Lo mismo que hace RabbitTemplate: convierte el objeto con SimpleMessageConverter y aplica el post-procesador.
            Message message = new SimpleMessageConverter().toMessage(invocation.getArgument(2), new MessageProperties());
            sent.add(new Sent(routingKey, processor.postProcessMessage(message)));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
        return template;
    }

    private JsonNode bodyOf(Sent one) {
        try {
            return objectMapper.readTree(new String(one.message().getBody(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("El cuerpo del evento " + one.routingKey() + " no es JSON", e);
        }
    }

    private Map<String, Object> contract() throws Exception {
        try (InputStream in = new ClassPathResource(CONTRACT).getInputStream()) {
            return new Yaml().load(in);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> schemas(Map<String, Object> contract) {
        return (Map<String, Object>) ((Map<String, Object>) contract.get("components")).get("schemas");
    }

    /** Sigue un {@code $ref} del tipo {@code #/components/messages/SaleCompleted}; si el nodo no es una referencia, lo devuelve igual. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolve(Map<String, Object> contract, Object node) {
        Map<String, Object> current = (Map<String, Object>) node;
        while (current.containsKey("$ref")) {
            Object target = contract;
            for (String step : String.valueOf(current.get("$ref")).substring(2).split("/")) {
                target = ((Map<String, Object>) target).get(step);
            }
            current = (Map<String, Object>) target;
        }
        return current;
    }

    private static String typeOf(JsonNode node) {
        if (node.isIntegralNumber()) {
            return "integer";
        }
        if (node.isNumber()) {
            return "number";
        }
        if (node.isTextual()) {
            return "string";
        }
        if (node.isObject()) {
            return "object";
        }
        return node.getNodeType().name().toLowerCase();
    }

    private static boolean isInstant(String text) {
        try {
            Instant.parse(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
