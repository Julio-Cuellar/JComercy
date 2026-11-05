package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.companymanagement.infra.outbox.OutboxDispatcher;
import com.jcode.jcomercy.companymanagement.infra.persistence.entity.OutboxEventJpaEntity;
import com.jcode.jcomercy.companymanagement.infra.persistence.repository.OutboxSpringDataRepository;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
 * Fase R4: lo que el Outbox de company-management publica, contra {@code asyncapi-company-management-v1.yaml}. El
 * contrato documenta también eventos anticipados que todavía no se emiten, así que solo se exige en un sentido: cada
 * evento que el módulo emite de verdad debe estar declarado y publicarse como el contrato dice.
 *
 * <p>No necesita Docker: un {@code RabbitTemplate} falso aplica el mismo post-procesador de mensajes que aplicaría el
 * real y guarda lo que habría salido hacia el broker (clave de enrutamiento, propiedades y cuerpo).
 */
@SpringBootTest(properties = "outbox.publisher.fixed-delay-ms=3600000")
@AutoConfigureMockMvc
class OutboxEventsContractIntegrationTest {

    private static final String CONTRACT = "asyncapi-company-management-v1.yaml";

    /** Los eventos que se emiten en esta prueba y el contrato debe cubrir. */
    private static final Set<String> EMITTED = Set.of("account.created", "account.suspended", "company.created",
        "company.activated", "branch.created", "branch.activated");

    private record Sent(String routingKey, Message message) { }

    private static final List<Sent> SENT = new CopyOnWriteArrayList<>();

    /** Guarda lo que el despachador enviaría y confirma al instante. */
    @TestConfiguration
    static class CapturingBroker {
        @Bean
        RabbitTemplate capturingBrokerTemplate() {
            RabbitTemplate template = mock(RabbitTemplate.class);
            doAnswer(invocation -> {
                String routingKey = invocation.getArgument(1);
                MessagePostProcessor processor = invocation.getArgument(3);
                CorrelationData correlation = invocation.getArgument(4);
                // Lo mismo que hace RabbitTemplate: convierte el objeto con SimpleMessageConverter y aplica el post-procesador.
                Message message = new SimpleMessageConverter().toMessage(invocation.getArgument(2), new MessageProperties());
                SENT.add(new Sent(routingKey, processor.postProcessMessage(message)));
                correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
                return null;
            }).when(template).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
            return template;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OutboxSpringDataRepository outbox;

    @Autowired
    private OutboxDispatcher dispatcher;

    @Test
    @DisplayName("Cada evento que el módulo emite está declarado en el AsyncAPI y se publica como el sobre que el contrato exige")
    @SuppressWarnings("unchecked")
    void everyEmittedEventIsDeclaredAndPublishedAsTheContractRequires() throws Exception {
        RestWorld world = new RestWorld(mockMvc, objectMapper, jdbc);
        RestWorld.Person owner = world.register();
        String login = world.login(owner.email());
        String company = world.createCompany(owner.accountId(), login);
        String companyBearer = world.enter(owner.accountId(), company, login);
        String branch = world.createBranch(company, companyBearer, "MATRIZ");
        world.activateBranch(company, branch, companyBearer);
        world.activateCompany(company, companyBearer);
        RestWorld.Person other = world.register();
        suspendAccount(other.accountId(), world.login(other.email()));

        Set<String> mine = outbox.findAll().stream()
            .filter(event -> owner.accountId().equals(event.getAccountId()) || other.accountId().equals(event.getAccountId())
                || company.equals(event.getCompanyId()))
            .map(OutboxEventJpaEntity::getEventId)
            .collect(Collectors.toSet());
        assertThat(mine).as("eventos que dejaron los flujos de la prueba").isNotEmpty();

        SENT.clear();
        // Pueden quedar pendientes de otras clases delante, o en espera de reintento: se despacha hasta que salgan todos.
        long deadline = System.currentTimeMillis() + 60_000;
        while (sentFrom(mine).size() < mine.size() && System.currentTimeMillis() < deadline) {
            dispatcher.publishPending();
            Thread.sleep(200);
        }
        List<Sent> sent = sentFrom(mine);

        Map<String, Object> contract = contract();
        Map<String, Object> envelope = (Map<String, Object>) schemas(contract).get("EventEnvelope");
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(sent.stream().map(Sent::routingKey).collect(Collectors.toSet()))
            .as("tipos de evento que salieron").containsAll(EMITTED);
        for (Sent one : sent) {
            check(soft, contract, envelope, one);
        }
        soft.assertAll();
    }

    // ---- Contraste con el contrato ----

    @SuppressWarnings("unchecked")
    private void check(SoftAssertions soft, Map<String, Object> contract, Map<String, Object> envelope, Sent sent) throws Exception {
        String type = sent.routingKey();
        MessageProperties properties = sent.message().getMessageProperties();

        // El tipo de evento tiene canal y mensaje en el contrato.
        Map<String, Object> channel = (Map<String, Object>) ((Map<String, Object>) contract.get("channels")).get("events/" + type.replace('.', '/'));
        soft.assertThat(channel).as("%s: canal declarado en el contrato", type).isNotNull();
        if (channel == null) {
            return;
        }
        Map<String, Object> message = resolve(contract, ((Map<String, Object>) channel.get("publish")).get("message"));
        String expectedContentType = String.valueOf(message.getOrDefault("contentType", contract.get("defaultContentType")));
        soft.assertThat(properties.getContentType()).as("%s: tipo de contenido", type).isEqualTo(expectedContentType);

        // El cuerpo es el sobre del contrato, con todos sus campos obligatorios y del tipo que dice.
        JsonNode body = objectMapper.readTree(new String(sent.message().getBody(), StandardCharsets.UTF_8));
        Map<String, Object> envelopeProperties = (Map<String, Object>) envelope.get("properties");
        for (String field : (List<String>) envelope.get("required")) {
            soft.assertThat(body.hasNonNull(field)).as("%s: campo obligatorio «%s» en el cuerpo", type, field).isTrue();
            if (body.hasNonNull(field)) {
                String expectedType = String.valueOf(((Map<String, Object>) envelopeProperties.get(field)).get("type"));
                soft.assertThat(typeOf(body.get(field))).as("%s: tipo de «%s»", type, field).isEqualTo(expectedType);
            }
        }
        if (body.hasNonNull("occurredAt")) {
            soft.assertThat(isInstant(body.get("occurredAt").asText())).as("%s: occurredAt es una fecha y hora", type).isTrue();
        }

        // Lo que el cuerpo dice es lo que dicen el identificador y las cabeceras del mensaje.
        soft.assertThat(body.path("eventId").asText()).as("%s: eventId del cuerpo y messageId", type).isEqualTo(properties.getMessageId());
        soft.assertThat(body.path("eventType").asText()).as("%s: eventType del cuerpo y clave de enrutamiento", type).isEqualTo(type);
        soft.assertThat(body.path("schemaVersion").asText()).as("%s: schemaVersion del cuerpo y de la cabecera", type)
            .isEqualTo(String.valueOf(properties.getHeaders().get("schemaVersion")));

        // El estado que lleva el evento es uno de los que el contrato declara para ese dato.
        Map<String, Object> dataSchema = dataSchemaOf(contract, message);
        if (dataSchema != null && body.path("data").hasNonNull("status")) {
            Map<String, Object> status = (Map<String, Object>) ((Map<String, Object>) dataSchema.get("properties")).get("status");
            if (status != null && status.get("enum") != null) {
                soft.assertThat((List<String>) status.get("enum")).as("%s: estados que declara el contrato", type)
                    .contains(body.path("data").get("status").asText());
            }
        }
    }

    /** El esquema del {@code data} del mensaje: el segundo elemento del {@code allOf} de su sobre. */
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

    private List<Sent> sentFrom(Set<String> eventIds) {
        return SENT.stream().filter(sent -> eventIds.contains(sent.message().getMessageProperties().getMessageId())).toList();
    }

    private void suspendAccount(String accountId, String bearer) throws Exception {
        mockMvc.perform(post("/api/v1/accounts/{id}/suspension", accountId)
                .header("Authorization", bearer).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Prueba de contrato de eventos\"}"))
            .andExpect(status().isOk());
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

    /** Sigue un {@code $ref} del tipo {@code #/components/messages/AccountCreated}; si el nodo no es una referencia, lo devuelve igual. */
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
