package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.ArrayList;
import java.util.Comparator;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
        "company.activated", "branch.created", "branch.activated", "role.created", "role.updated", "role.deactivated",
        "role.deleted", "membership.invitation.created", "membership.invitation.accepted", "membership.invitation.revoked",
        "membership.created", "branch-assignment.created", "branch-assignment.updated", "branch-assignment.revoked");

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

    @Autowired
    private com.jcode.jcomercy.companymanagement.infra.mail.RecordingMailAdapter mail;

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
        // Los cuatro eventos de roles: crear, editar, desactivar y borrar un rol propio.
        String cashier = createRole(company, companyBearer, "Cajero del contrato");
        mockMvc.perform(patch("/api/v1/companies/{company}/roles/{role}", company, cashier)
                .header("Authorization", companyBearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cajero del contrato\",\"permissions\":[{\"permissionCode\":\"sales.read\"},{\"permissionCode\":\"sales.cancel\"}]}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/companies/{company}/roles/{role}/deactivation", company, cashier)
                .header("Authorization", companyBearer).header("Idempotency-Key", UUID.randomUUID().toString()))
            .andExpect(status().isOk());
        String disposable = createRole(company, companyBearer, "Rol desechable del contrato");
        mockMvc.perform(delete("/api/v1/companies/{company}/roles/{role}?confirmation=true", company, disposable)
                .header("Authorization", companyBearer).header("Idempotency-Key", UUID.randomUUID().toString()))
            .andExpect(status().isNoContent());
        // Los eventos de invitación y membresía: una persona activa su acceso con el enlace y a otra se le cancela la invitación.
        ObjectNode activated = registerEmployee(company, companyBearer, branch);
        mockMvc.perform(post("/api/v1/employee-invitations/{code}/acceptance", activationCodeSentTo(activated.get("email").asText()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + RestWorld.PASSWORD + "\"}"))
            .andExpect(status().isNoContent());
        ObjectNode cancelled = registerEmployee(company, companyBearer, branch);
        mockMvc.perform(delete("/api/v1/companies/{company}/employees/{employee}/invitation", company, cancelled.get("employeeId").asText())
                .header("Authorization", companyBearer).header("Idempotency-Key", UUID.randomUUID().toString()))
            .andExpect(status().isNoContent());
        // Los eventos de las sucursales de una persona: agregar una, suspenderla y quitarla.
        String membershipOfActivated = jdbc.queryForObject("SELECT membership_public_id FROM company_management.employee_profile WHERE public_employee_id=?",
            String.class, activated.get("employeeId").asText());
        String secondBranch = world.createBranch(company, companyBearer, "SUCURSAL");
        String assignment = objectMapper.readTree(mockMvc.perform(post("/api/v1/companies/{company}/memberships/{membership}/assignments", company, membershipOfActivated)
                .header("Authorization", companyBearer).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"branchId\":\"" + secondBranch + "\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("assignmentId").asText();
        mockMvc.perform(patch("/api/v1/companies/{company}/memberships/{membership}/assignments/{assignment}", company, membershipOfActivated, assignment)
                .header("Authorization", companyBearer).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/companies/{company}/memberships/{membership}/assignments/{assignment}", company, membershipOfActivated, assignment)
                .header("Authorization", companyBearer).header("Idempotency-Key", UUID.randomUUID().toString()))
            .andExpect(status().isNoContent());
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
        // «sequence» crece con el orden en que se registran los eventos (ERR-024): no es una constante.
        List<Long> sequences = outbox.findAll().stream()
            .filter(event -> mine.contains(event.getEventId()))
            .sorted(Comparator.comparing(OutboxEventJpaEntity::getCreatedAt))
            .map(OutboxEventJpaEntity::getSequence)
            .toList();
        for (int i = 1; i < sequences.size(); i++) {
            soft.assertThat(sequences.get(i)).as("sequence del evento %d frente al anterior (%s)", i, sequences).isGreaterThan(sequences.get(i - 1));
        }
        soft.assertAll();
    }

    /** Registra a una persona nueva y devuelve su respuesta con el correo con el que se registró. */
    private ObjectNode registerEmployee(String company, String bearer, String branch) throws Exception {
        int n = RestWorld.next();
        String homoclave = Integer.toString(n % 46656, 36).toUpperCase(java.util.Locale.ROOT);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("firstName", "Persona" + n).put("lastName", "Contrato Ruiz").put("birthDate", "1990-05-17");
        body.put("rfc", "PRUE850412" + "0".repeat(3 - homoclave.length()) + homoclave);
        body.put("curp", "PRUE850412HDFRRN" + Integer.toString((n / 10) % 36, 36).toUpperCase(java.util.Locale.ROOT) + (n % 10));
        body.put("phoneNumber", "5512345678").put("email", "contrato-" + n + "@example.com").put("state", "Jalisco").put("address", "Calle 1");
        body.putArray("roleIds").add("rol_sys_staff");
        body.putArray("branchIds").add(branch);
        String response = mockMvc.perform(post("/api/v1/companies/{company}/employees", company)
                .header("Authorization", bearer).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return (ObjectNode) objectMapper.readTree(response);
    }

    /** El código del enlace que llegó al correo de la persona. */
    private String activationCodeSentTo(String email) {
        String body = mail.sentTo(email).get(0).body();
        String marker = "activar-acceso?codigo=";
        int start = body.indexOf(marker) + marker.length();
        int end = body.indexOf('\n', start);
        return body.substring(start, end < 0 ? body.length() : end).trim();
    }

    private String createRole(String company, String bearer, String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/companies/{company}/roles", company)
                .header("Authorization", bearer).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"permissions\":[{\"permissionCode\":\"sales.read\"}]}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("roleId").asText();
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
            Map<String, Object> fieldSchema = (Map<String, Object>) envelopeProperties.get(field);
            soft.assertThat(body.has(field)).as("%s: campo obligatorio «%s» en el cuerpo", type, field).isTrue();
            // Un campo obligatorio puede ser nulo solo si el contrato lo declara «nullable»: presente no basta (ERR-024).
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
        // La cuenta es de quien emite el evento siempre; la empresa solo falta en los eventos de cuenta, donde aún no existe.
        if (type.startsWith("account.")) {
            soft.assertThat(body.path("companyId").isNull()).as("%s: sin empresa, companyId es nulo y no vacío", type).isTrue();
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

        // Los nombres del data son los que el contrato declara para ese dato (ERR-025): uno que no declara no lo puede leer un consumidor.
        if (dataSchema != null) {
            Set<String> declared = ((Map<String, Object>) dataSchema.get("properties")).keySet();
            List<String> undeclared = new ArrayList<>();
            body.path("data").fieldNames().forEachRemaining(name -> {
                if (!declared.contains(name)) {
                    undeclared.add(name);
                }
            });
            soft.assertThat(undeclared).as("%s: campos del data que el contrato no declara", type).isEmpty();
        }
        if (type.equals("branch.created")) {
            soft.assertThat(body.path("data").path("isHeadOffice").isBoolean()).as("%s: isHeadOffice booleano en el data", type).isTrue();
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
