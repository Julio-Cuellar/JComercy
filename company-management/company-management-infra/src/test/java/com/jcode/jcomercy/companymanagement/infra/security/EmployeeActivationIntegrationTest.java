package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jcode.jcomercy.companymanagement.domain.port.MailPort;
import com.jcode.jcomercy.companymanagement.infra.mail.RecordingMailAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M5 del frente de roles: RH registra a una persona, el sistema le manda un enlace por correo y ella solo elige su
 * contraseña para entrar. Al activar se crea su cuenta (o se usa la que ya tiene), su membresía y sus sucursales.
 * El enlace sirve una vez, vence y, si ya no sirve, responde igual sin importar por qué.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EmployeeActivationIntegrationTest {

    private static final String LINK = "https://app.jcomercy.test/activar-acceso?codigo=";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RecordingMailAdapter mail;

    private RestWorld world;
    private String company;
    private String ownerToken;
    private String branch;

    @BeforeEach
    void company() throws Exception {
        world = new RestWorld(mockMvc, objectMapper, jdbc);
        RestWorld.Person owner = world.register();
        String account = world.login(owner.email());
        company = world.createCompany(owner.accountId(), account);
        ownerToken = world.enter(owner.accountId(), company, account);
        branch = world.createBranch(company, ownerToken, "MATRIZ");
        world.activateBranch(company, branch, ownerToken);
        world.activateCompany(company, ownerToken);
    }

    @Test
    @DisplayName("Al registrar a la persona le llega un correo con el enlace de activación y RH ve que se envió")
    void registeringSendsTheActivationLink() throws Exception {
        ObjectNode body = employee();
        String id = register(body);

        MailPort.Message message = mail.sentTo(body.get("email").asText()).get(0);
        assertThat(message.subject()).contains("JComercy");
        assertThat(message.body()).contains(LINK).contains("7 días").contains("una sola vez").contains(body.get("firstName").asText());
        assertThat(message.body()).as("el correo no lleva datos personales que no hacen falta")
            .doesNotContain(body.get("rfc").asText()).doesNotContain(body.get("curp").asText());

        call(HttpMethod.GET, "/employees/" + id, ownerToken).andExpect(status().isOk())
            .andExpect(jsonPath("$.invitation.status").value("PENDING"))
            .andExpect(jsonPath("$.invitation.emailStatus").value("SENT"))
            .andExpect(jsonPath("$.invitation.expiresAt").exists());
    }

    @Test
    @DisplayName("Quien abre el enlace ve su nombre, la empresa, sus roles y su correo")
    void theLinkShowsWhoIsBeingInvited() throws Exception {
        ObjectNode body = employee();
        register(body);

        consult(codeOf(body)).andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value(body.get("firstName").asText()))
            .andExpect(jsonPath("$.companyName").exists())
            .andExpect(jsonPath("$.roleNames[0]").value("STAFF"))
            .andExpect(jsonPath("$.email").value(body.get("email").asText()))
            .andExpect(jsonPath("$.accountExists").value(false));
    }

    @Test
    @DisplayName("Un enlace vencido, usado, cancelado o inexistente responde igual, sin decir por qué")
    void anInvalidLinkAnswersTheSameWhateverTheReason() throws Exception {
        JsonNode unknown = json(consult("codigo-que-nadie-recibio").andExpect(status().isNotFound()));

        ObjectNode used = employee();
        register(used);
        String usedCode = codeOf(used);
        accept(usedCode, RestWorld.PASSWORD, null).andExpect(status().isNoContent());
        ObjectNode cancelled = employee();
        String cancelledId = register(cancelled);
        String cancelledCode = codeOf(cancelled);
        send(HttpMethod.DELETE, "/employees/" + cancelledId + "/invitation", ownerToken, null, UUID.randomUUID().toString()).andExpect(status().isNoContent());
        ObjectNode expired = employee();
        String expiredId = register(expired);
        String expiredCode = codeOf(expired);
        jdbc.update("UPDATE company_management.employee_invitation SET expires_at=? WHERE public_employee_id=?",
            OffsetDateTime.parse("2020-01-01T00:00:00Z"), expiredId);

        for (String code : List.of(usedCode, cancelledCode, expiredCode)) {
            JsonNode answer = json(consult(code).andExpect(status().isNotFound()));
            for (String field : List.of("type", "title", "status", "detail", "code")) {
                assertThat(answer.path(field)).as("«%s» igual que con un código inexistente", field).isEqualTo(unknown.path(field));
            }
            accept(code, RestWorld.PASSWORD, null).andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("Al activar con una contraseña se crea su cuenta, su membresía y sus sucursales, y entra a la empresa con su rol")
    void acceptingCreatesTheAccountTheMembershipAndTheBranches() throws Exception {
        ObjectNode body = employee();
        String id = register(body);
        String email = body.get("email").asText();
        String code = codeOf(body);

        accept(code, RestWorld.PASSWORD, null).andExpect(status().isNoContent());

        String accountBearer = world.login(email);
        String accountId = jdbc.queryForObject("SELECT account_public_id FROM company_management.employee_profile WHERE public_employee_id=?", String.class, id);
        String companyBearer = world.enter(accountId, company, accountBearer);
        JsonNode claims = world.claimsOf(companyBearer);
        assertThat(claims.get("permissions").asText()).contains("operational_context.select");
        assertThat(claims.get("ctx").asText()).isEqualTo(company);
        List<String> branches = new ArrayList<>();
        claims.get("branchIds").forEach(node -> branches.add(node.asText()));
        assertThat(branches).containsExactly(branch);

        call(HttpMethod.GET, "/employees/" + id, ownerToken).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.invitation.status").value("ACCEPTED"));
        call(HttpMethod.GET, "/memberships?status=ACTIVE", ownerToken).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.userId=='" + accountId + "')].roleIds[0]", hasItem("rol_sys_staff")));

        // El enlace ya no sirve.
        accept(code, RestWorld.PASSWORD, null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Una contraseña corta se rechaza explicando qué falta y el enlace sigue sirviendo")
    void aShortPasswordIsRejectedAndTheLinkStillWorks() throws Exception {
        ObjectNode body = employee();
        register(body);

        accept(codeOf(body), "corta", null).andExpect(status().isUnprocessableEntity());
        accept(codeOf(body), "", null).andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_management.owner_person WHERE email=?", Integer.class, body.get("email").asText()))
            .as("no quedó ninguna cuenta a medias").isZero();

        accept(codeOf(body), RestWorld.PASSWORD, null).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Si la empresa no está activa, no se puede activar el acceso")
    void anInactiveCompanyCannotBeEnteredYet() throws Exception {
        RestWorld.Person other = world.register();
        String otherAccount = world.login(other.email());
        String otherCompany = world.createCompany(other.accountId(), otherAccount);
        String otherToken = world.enter(other.accountId(), otherCompany, otherAccount);
        String otherBranch = world.createBranch(otherCompany, otherToken, "MATRIZ");
        ObjectNode body = employee();
        body.set("branchIds", ids(otherBranch));
        send(HttpMethod.POST, "/employees", otherToken, body.toString(), UUID.randomUUID().toString(), otherCompany).andExpect(status().isCreated());

        accept(codeOf(body), RestWorld.PASSWORD, null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Si la persona ya tiene cuenta, entra con ella: sin sesión se le pide iniciarla y con otra cuenta se rechaza")
    void aPersonWithAnAccountMustUseItsOwnSession() throws Exception {
        String rfc = rfc();
        String email = "existente-" + RestWorld.next() + "@example.com";
        String accountId = registerAccount(email, rfc);
        ObjectNode body = employee().put("email", email).put("rfc", rfc);
        register(body);
        String code = codeOf(body);

        consult(code).andExpect(status().isOk()).andExpect(jsonPath("$.accountExists").value(true));
        accept(code, null, null).andExpect(status().isUnauthorized());
        RestWorld.Person stranger = world.register();
        accept(code, null, world.login(stranger.email())).andExpect(status().isForbidden());

        accept(code, null, world.login(email)).andExpect(status().isNoContent());
        call(HttpMethod.GET, "/memberships?status=ACTIVE", ownerToken).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.userId=='" + accountId + "')]").exists());
        // Su contraseña no se tocó.
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + RestWorld.PASSWORD + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Si la persona ya pertenece a la empresa, activar da conflicto y no duplica la membresía")
    void anExistingMembershipIsAConflict() throws Exception {
        String rfc = rfc();
        String email = "ya-miembro-" + RestWorld.next() + "@example.com";
        String accountId = registerAccount(email, rfc);
        ObjectNode body = employee().put("email", email).put("rfc", rfc);
        register(body);
        world.addMember(company, accountId, "ACTIVE", "[\"STAFF\"]");

        accept(codeOf(body), null, world.login(email)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Si el rol se desactivó antes de activar, se rechaza y no queda nada a medias")
    void aRoleDeactivatedBeforeActivationBlocksIt() throws Exception {
        String roleId = customRole("Cajero RH");
        ObjectNode body = employee();
        body.set("roleIds", ids(roleId));
        register(body);
        send(HttpMethod.POST, "/roles/" + roleId + "/deactivation", ownerToken, null, UUID.randomUUID().toString()).andExpect(status().isOk());

        accept(codeOf(body), RestWorld.PASSWORD, null).andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_management.owner_person WHERE email=?", Integer.class, body.get("email").asText())).isZero();
    }

    @Test
    @DisplayName("Reenviar manda un enlace nuevo y el anterior deja de servir")
    void resendingSendsANewLinkAndKillsTheOldOne() throws Exception {
        ObjectNode body = employee();
        String id = register(body);
        String first = codeOf(body);

        send(HttpMethod.POST, "/employees/" + id + "/invitation", ownerToken, null, UUID.randomUUID().toString()).andExpect(status().isOk())
            .andExpect(jsonPath("$.invitation.status").value("PENDING"))
            .andExpect(jsonPath("$.invitation.emailStatus").value("SENT"));

        List<MailPort.Message> messages = mail.sentTo(body.get("email").asText());
        assertThat(messages).hasSize(2);
        String second = codeIn(messages.get(1));
        assertThat(second).isNotEqualTo(first);
        consult(first).andExpect(status().isNotFound());
        consult(second).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_management.employee_invitation WHERE public_employee_id=? AND status='PENDING'", Integer.class, id))
            .as("solo una invitación vigente por persona").isEqualTo(1);
    }

    @Test
    @DisplayName("Cancelar deja el enlace sin efecto, la persona sigue pendiente y se puede volver a invitar")
    void cancellingKillsTheLinkAndAllowsInvitingAgain() throws Exception {
        ObjectNode body = employee();
        String id = register(body);
        String code = codeOf(body);

        send(HttpMethod.DELETE, "/employees/" + id + "/invitation", ownerToken, null, UUID.randomUUID().toString()).andExpect(status().isNoContent());
        consult(code).andExpect(status().isNotFound());
        call(HttpMethod.GET, "/employees/" + id, ownerToken).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"))
            .andExpect(jsonPath("$.invitation.status").value("CANCELLED"));
        send(HttpMethod.DELETE, "/employees/" + id + "/invitation", ownerToken, null, UUID.randomUUID().toString()).andExpect(status().isConflict());

        send(HttpMethod.POST, "/employees/" + id + "/invitation", ownerToken, null, UUID.randomUUID().toString()).andExpect(status().isOk());
        consult(codeIn(mail.sentTo(body.get("email").asText()).get(1))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Reenviar o cancelar una persona ya activa se rechaza, y el personal sin permiso de RH no puede")
    void resendingAnActiveOneIsRejectedAndStaffCannot() throws Exception {
        ObjectNode body = employee();
        String id = register(body);
        RestWorld.Person staff = world.register();
        world.addMember(company, staff.accountId(), "ACTIVE", "[\"STAFF\"]");
        String staffToken = world.enter(staff.accountId(), company, world.login(staff.email()));
        send(HttpMethod.POST, "/employees/" + id + "/invitation", staffToken, null, UUID.randomUUID().toString()).andExpect(status().isForbidden());
        send(HttpMethod.DELETE, "/employees/" + id + "/invitation", staffToken, null, UUID.randomUUID().toString()).andExpect(status().isForbidden());

        accept(codeOf(body), RestWorld.PASSWORD, null).andExpect(status().isNoContent());
        send(HttpMethod.POST, "/employees/" + id + "/invitation", ownerToken, null, UUID.randomUUID().toString()).andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Ningún evento lleva el RFC, la CURP, el correo ni la contraseña, y los de invitación y membresía sí salen")
    void noEventCarriesPersonalDataAndTheMembershipEventsAreRecorded() throws Exception {
        ObjectNode body = employee();
        register(body);
        accept(codeOf(body), RestWorld.PASSWORD, null).andExpect(status().isNoContent());
        ObjectNode cancelled = employee();
        String cancelledId = register(cancelled);
        send(HttpMethod.DELETE, "/employees/" + cancelledId + "/invitation", ownerToken, null, UUID.randomUUID().toString()).andExpect(status().isNoContent());

        List<String> types = jdbc.queryForList("SELECT event_type FROM company_management.outbox_event WHERE company_id=?", String.class, company);
        assertThat(types).contains("membership.invitation.created", "membership.created", "membership.invitation.accepted",
            "membership.invitation.revoked", "branch-assignment.created");

        String everything = String.join("\n", jdbc.queryForList("SELECT payload_json FROM company_management.outbox_event", String.class));
        for (ObjectNode person : List.of(body, cancelled)) {
            assertThat(everything).doesNotContain(person.get("rfc").asText()).doesNotContain(person.get("curp").asText())
                .doesNotContain(person.get("email").asText());
        }
        assertThat(everything).doesNotContain(RestWorld.PASSWORD);
    }

    // ---- Ayudas ----

    private String register(ObjectNode body) throws Exception {
        String created = send(HttpMethod.POST, "/employees", ownerToken, body.toString(), UUID.randomUUID().toString())
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("employeeId").asText();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** El código del último correo que se mandó a esa persona. */
    private String codeOf(ObjectNode body) {
        List<MailPort.Message> messages = mail.sentTo(body.get("email").asText());
        assertThat(messages).as("correos mandados a la persona").isNotEmpty();
        return codeIn(messages.get(messages.size() - 1));
    }

    private static String codeIn(MailPort.Message message) {
        Matcher matcher = Pattern.compile(Pattern.quote(LINK) + "(\\S+)").matcher(message.body());
        assertThat(matcher.find()).as("el correo trae el enlace con el código").isTrue();
        return matcher.group(1);
    }

    private ResultActions consult(String code) throws Exception {
        return mockMvc.perform(request(HttpMethod.GET, "/api/v1/employee-invitations/" + code));
    }

    private ResultActions accept(String code, String password, String bearer) throws Exception {
        MockHttpServletRequestBuilder builder = post("/api/v1/employee-invitations/" + code + "/acceptance")
            .contentType(MediaType.APPLICATION_JSON)
            .content(password == null ? "{}" : "{\"password\":\"" + password + "\"}");
        if (bearer != null) {
            builder.header("Authorization", bearer);
        }
        return mockMvc.perform(builder);
    }

    private ObjectNode employee() {
        int n = RestWorld.next();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("firstName", "Empleada" + n);
        body.put("lastName", "Prueba Ruiz");
        body.put("birthDate", "1990-05-17");
        body.put("rfc", rfc());
        body.put("curp", curp(n));
        body.put("phoneNumber", "5512345678");
        body.put("email", "empleado-" + n + "@example.com");
        body.put("state", "Jalisco");
        body.put("address", "Calle Hidalgo 12, Centro");
        body.set("roleIds", ids("rol_sys_staff"));
        body.set("branchIds", ids(branch));
        return body;
    }

    private static String rfc() {
        String homoclave = Integer.toString(RestWorld.next() % 46656, 36).toUpperCase(Locale.ROOT);
        return "PRUE850412" + "0".repeat(3 - homoclave.length()) + homoclave;
    }

    private static String curp(int n) {
        String letter = Integer.toString((n / 10) % 36, 36).toUpperCase(Locale.ROOT);
        return "PRUE850412HDFRRN" + letter + (n % 10);
    }

    private ArrayNode ids(String... values) {
        ArrayNode array = objectMapper.createArrayNode();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private String customRole(String name) throws Exception {
        String created = send(HttpMethod.POST, "/roles", ownerToken,
            "{\"name\":\"" + name + "\",\"permissions\":[{\"permissionCode\":\"inventory.stock.read\"}]}", UUID.randomUUID().toString())
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("roleId").asText();
    }

    private String registerAccount(String email, String rfc) throws Exception {
        int n = RestWorld.next();
        String body = """
            {"name":"Cuenta %d","owner":{"firstName":"Persona","lastName":"Existente","birthDate":"1985-04-12",
            "rfc":"%s","phoneNumber":"5512345678","email":"%s","password":"%s","state":"Jalisco","address":"Av. Reforma 100"}}
            """.formatted(n, rfc, email, RestWorld.PASSWORD);
        String response = mockMvc.perform(post("/api/v1/accounts/registration").header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("publicAccountId").asText();
    }

    private ResultActions call(HttpMethod method, String path, String bearer) throws Exception {
        return send(method, path, bearer, null, null);
    }

    private ResultActions send(HttpMethod method, String path, String bearer, String body, String idempotencyKey) throws Exception {
        return send(method, path, bearer, body, idempotencyKey, null);
    }

    private ResultActions send(HttpMethod method, String path, String bearer, String body, String idempotencyKey, String companyId) throws Exception {
        MockHttpServletRequestBuilder builder = request(method, "/api/v1/companies/" + (companyId == null ? company : companyId) + path)
            .header("Authorization", bearer);
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(builder);
    }
}
