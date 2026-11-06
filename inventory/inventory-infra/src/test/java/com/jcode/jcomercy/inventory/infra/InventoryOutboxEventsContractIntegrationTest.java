package com.jcode.jcomercy.inventory.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jcode.jcomercy.inventory.application.service.ReservationApplicationService;
import com.jcode.jcomercy.inventory.infra.outbox.InventoryOutboxDispatcher;
import com.jcode.jcomercy.inventory.infra.persistence.entity.InventoryOutboxEventJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.InventoryOutboxEventSpringDataRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.assertj.core.api.SoftAssertions;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.yaml.snakeyaml.Yaml;

import javax.crypto.SecretKey;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lo que el Outbox de inventario publica, contra {@code asyncapi-inventory-v1.yaml} (ERR-026). Igual que la prueba de
 * company-management, solo se exige en un sentido: cada evento que el módulo emite de verdad debe estar declarado y
 * publicarse como el contrato dice. No necesita Docker: un {@code RabbitTemplate} falso aplica el mismo post-procesador
 * y guarda lo que habría salido hacia el broker.
 */
@SpringBootTest(properties = "server.port=0")
@AutoConfigureMockMvc
class InventoryOutboxEventsContractIntegrationTest {

    private static final String CONTRACT = "asyncapi-inventory-v1.yaml";
    private static final String SECRET = "local-only-change-me-012345678901234567890123456789";

    /** Los eventos que los flujos de esta prueba emiten y el contrato debe cubrir. */
    private static final Set<String> EMITTED = Set.of("inventory.warehouse.created", "inventory.warehouse.activated",
        "inventory.stock.received", "inventory.stock.adjusted", "inventory.stock.issued", "inventory.reservation.created",
        "inventory.reservation.released", "inventory.reservation.committed", "inventory.reservation.expired");

    private record Sent(String routingKey, Message message) { }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private InventoryOutboxEventSpringDataRepository outbox;
    @Autowired private ReservationApplicationService reservations;

    @Test
    @DisplayName("Cada evento que inventario emite está declarado en el AsyncAPI y se publica como el sobre que el contrato exige")
    @SuppressWarnings("unchecked")
    void everyEmittedEventIsDeclaredAndPublishedAsTheContractRequires() throws Exception {
        outbox.deleteAll();
        String company = "cmp_ctr_" + UUID.randomUUID().toString().substring(0, 8);
        String branch = "br_ctr_1";
        String token = token(company, branch);

        String warehouse = JsonPath.read(create(token, "/api/v1/companies/" + company + "/branches/" + branch + "/warehouses",
            """
            {"code":"ALM-CTR-01","name":"Almacén de contrato","type":"GENERAL","defaultWarehouse":false,
             "timezone":"America/Mexico_City","currency":"MXN"}
            """), "$.id");
        mockMvc.perform(post("/api/v1/warehouses/{id}/activate", warehouse)
                .header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString()))
            .andExpect(status().isOk());
        String location = JsonPath.read(create(token, "/api/v1/warehouses/" + warehouse + "/locations",
            "{\"code\":\"PASILLO-A\",\"name\":\"Pasillo A\",\"type\":\"INTERNAL\"}"), "$.id");
        String product = JsonPath.read(create(token, "/api/v1/companies/" + company + "/products",
            """
            {"sku":"CTR-SKU-1","name":"Producto de contrato","stockUnitCode":"PZA","trackingMode":"NONE","allowNegativeStock":false}
            """), "$.id");
        String stock = "/api/v1/companies/" + company + "/branches/" + branch + "/stock/";
        create(token, stock + "receipts", """
            {"warehouseId":"%s","locationId":"%s","productId":"%s","quantity":20,"unitCode":"PZA","unitCost":10.00,"inventoryStatus":"AVAILABLE"}
            """.formatted(warehouse, location, product));
        create(token, stock + "adjustments", """
            {"warehouseId":"%s","locationId":"%s","productId":"%s","quantity":1,"unitCode":"PZA","reason":"Merma","direction":"OUT","inventoryStatus":"AVAILABLE"}
            """.formatted(warehouse, location, product));

        String reservationsUrl = stock + "reservations";
        create(token, reservationsUrl, reservation(warehouse, product, "CTR-1", ""));
        reference(token, reservationsUrl + "/release", "CTR-1");
        create(token, reservationsUrl, reservation(warehouse, product, "CTR-2", ""));
        reference(token, reservationsUrl + "/commit", "CTR-2");
        create(token, reservationsUrl, reservation(warehouse, product, "CTR-3", ",\"expiresAt\":\"" + Instant.now().plusSeconds(3600) + "\""));
        assertThat(reservations.expireDue(Instant.now().plusSeconds(7200), 100)).isEqualTo(1);

        List<InventoryOutboxEventJpaEntity> mine = outbox.findAll().stream()
            .filter(event -> company.equals(event.getCompanyId()))
            .sorted(Comparator.comparing(InventoryOutboxEventJpaEntity::getCreatedAt))
            .toList();
        assertThat(mine).as("eventos que dejaron los flujos de la prueba").isNotEmpty();
        Set<String> mineIds = mine.stream().map(event -> event.getEventId().toString()).collect(Collectors.toSet());

        InventoryOutboxDispatcher dispatcher = new InventoryOutboxDispatcher(outbox, capturingTemplate(), "jcomercy.events");
        for (int round = 0; round < 5 && sentFrom(mineIds).size() < mineIds.size(); round++) {
            dispatcher.publishPending();
        }
        List<Sent> mineSent = sentFrom(mineIds);

        Map<String, Object> contract = contract();
        Map<String, Object> envelope = (Map<String, Object>) schemas(contract).get("EventEnvelope");
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(mineSent.size()).as("eventos publicados").isEqualTo(mineIds.size());
        soft.assertThat(mineSent.stream().map(Sent::routingKey).collect(Collectors.toSet()))
            .as("tipos de evento que salieron").containsAll(EMITTED);
        for (Sent one : mineSent) {
            check(soft, contract, envelope, one);
        }
        // «sequence» crece con el orden en que se registran los eventos.
        List<Long> sequences = new ArrayList<>();
        for (InventoryOutboxEventJpaEntity event : mine) {
            mineSent.stream()
                .filter(one -> event.getEventId().toString().equals(one.message().getMessageProperties().getMessageId()))
                .findFirst()
                .ifPresent(one -> sequences.add(bodyOf(one).path("sequence").asLong(-1)));
        }
        for (int i = 1; i < sequences.size(); i++) {
            soft.assertThat(sequences.get(i)).as("sequence del evento %d frente al anterior (%s)", i, sequences).isGreaterThan(sequences.get(i - 1));
        }
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
        soft.assertThat(body.path("producerModule").asText()).as("%s: producerModule", type).isEqualTo("inventory");
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
        Map<String, Object> status = (Map<String, Object>) dataProperties.get("status");
        if (status != null && status.get("enum") != null && body.path("data").hasNonNull("status")) {
            soft.assertThat((List<String>) status.get("enum")).as("%s: estados que declara el contrato", type)
                .contains(body.path("data").get("status").asText());
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

    private List<Sent> sentFrom(Set<String> eventIds) {
        return sent.stream().filter(one -> eventIds.contains(one.message().getMessageProperties().getMessageId())).toList();
    }

    private JsonNode bodyOf(Sent one) {
        try {
            return objectMapper.readTree(new String(one.message().getBody(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("El cuerpo del evento " + one.routingKey() + " no es JSON", e);
        }
    }

    private String create(String token, String url, String body) throws Exception {
        return perform(token, url, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private void reference(String token, String url, String reference) throws Exception {
        perform(token, url, "{\"referenceType\":\"ORDER_LINE\",\"referenceId\":\"%s\"}".formatted(reference)).andExpect(status().isOk());
    }

    private ResultActions perform(String token, String url, String body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
            .header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String reservation(String warehouse, String product, String reference, String extra) {
        return """
            {"warehouseId":"%s","productId":"%s","quantity":2,"unitCode":"PZA","referenceType":"ORDER_LINE","referenceId":"%s"%s}
            """.formatted(warehouse, product, reference, extra);
    }

    private static String token(String company, String branch) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
            .subject("user-contract-1").issuer("jcomercy")
            .claim("companyIds", List.of(company))
            .claim("branchIds", List.of(branch))
            .claim("permissions", "INVENTORY_WRITE INVENTORY_READ")
            .expiration(new Date(System.currentTimeMillis() + 3600_000))
            .signWith(key).compact();
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

    /** Sigue un {@code $ref} del tipo {@code #/components/messages/WarehouseCreated}; si el nodo no es una referencia, lo devuelve igual. */
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
        } catch (RuntimeException e) {
            return false;
        }
    }
}
