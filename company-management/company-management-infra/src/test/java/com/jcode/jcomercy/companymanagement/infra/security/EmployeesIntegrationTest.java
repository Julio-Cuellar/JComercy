package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M3 del frente de roles: RH registra al empleado con sus datos, su rol y sus sucursales; el empleado no llena nada.
 * El perfil queda «pendiente de activar» hasta que el empleado active su acceso (M5). RFC y CURP son datos personales:
 * solo los ve quien tiene permiso de RH y nunca salen en un evento.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EmployeesIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

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
    }

    @Test
    @DisplayName("RH registra a un empleado con sus datos, su rol y su sucursal, y queda pendiente de activar")
    void hrRegistersAnEmployeeWhoIsPendingActivation() throws Exception {
        ObjectNode body = employee();
        String created = create(ownerToken, body).andExpect(status().isCreated())
            .andExpect(jsonPath("$.employeeId").value(org.hamcrest.Matchers.startsWith("emp_")))
            .andExpect(jsonPath("$.profileType").value("EMPLEADO"))
            .andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"))
            .andExpect(jsonPath("$.firstName").value(body.get("firstName").asText()))
            .andExpect(jsonPath("$.rfc").value(body.get("rfc").asText()))
            .andExpect(jsonPath("$.curp").value(body.get("curp").asText()))
            .andExpect(jsonPath("$.email").value(body.get("email").asText()))
            .andExpect(jsonPath("$.roleIds[0]").value("rol_sys_staff"))
            .andExpect(jsonPath("$.branchIds[0]").value(branch))
            .andExpect(header().exists("Location"))
            .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).get("employeeId").asText();

        call(HttpMethod.GET, "/employees/" + id, ownerToken).andExpect(status().isOk())
            .andExpect(jsonPath("$.employeeId").value(id))
            .andExpect(jsonPath("$.curp").value(body.get("curp").asText()));
        assertThat(ids(call(HttpMethod.GET, "/employees", ownerToken))).containsExactly(id);
        assertThat(ids(call(HttpMethod.GET, "/employees?status=ACTIVE", ownerToken))).isEmpty();
    }

    @Test
    @DisplayName("Registrar exige la clave de idempotencia y repetirla no duplica al empleado")
    void registeringNeedsTheIdempotencyKeyAndRepeatingItDoesNotDuplicate() throws Exception {
        mockMvc.perform(request(HttpMethod.POST, "/api/v1/companies/" + company + "/employees")
                .header("Authorization", ownerToken).contentType(MediaType.APPLICATION_JSON).content(employee().toString()))
            .andExpect(status().isBadRequest());

        ObjectNode body = employee();
        String key = UUID.randomUUID().toString();
        String first = send(HttpMethod.POST, "/employees", ownerToken, body.toString(), key).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String replay = send(HttpMethod.POST, "/employees", ownerToken, body.toString(), key).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(replay).get("employeeId").asText())
            .isEqualTo(objectMapper.readTree(first).get("employeeId").asText());
        assertThat(ids(call(HttpMethod.GET, "/employees", ownerToken))).hasSize(1);
    }

    @Test
    @DisplayName("Solo quien tiene permiso de RH registra y consulta empleados: el administrador de empresa sí, el personal no")
    void onlyThoseWithHrPermissionManageEmployees() throws Exception {
        RestWorld.Person admin = world.register();
        world.addMember(company, admin.accountId(), "ACTIVE", "[\"COMPANY_ADMIN\"]");
        String adminToken = world.enter(admin.accountId(), company, world.login(admin.email()));
        create(adminToken, employee()).andExpect(status().isCreated());
        call(HttpMethod.GET, "/employees", adminToken).andExpect(status().isOk());

        RestWorld.Person staff = world.register();
        world.addMember(company, staff.accountId(), "ACTIVE", "[\"STAFF\"]");
        String staffToken = world.enter(staff.accountId(), company, world.login(staff.email()));
        create(staffToken, employee()).andExpect(status().isForbidden());
        call(HttpMethod.GET, "/employees", staffToken).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Los datos mal formados se rechazan: RFC, CURP, correo, nombres, fecha, rol y sucursal")
    void malformedDataIsRejected() throws Exception {
        create(ownerToken, employee().put("rfc", "ABC")).andExpect(status().isBadRequest());
        create(ownerToken, employee().put("curp", "CURP-MAL")).andExpect(status().isBadRequest());
        create(ownerToken, employee().put("email", "no-es-correo")).andExpect(status().isBadRequest());
        create(ownerToken, employee().put("firstName", " ")).andExpect(status().isBadRequest());
        create(ownerToken, employee().put("lastName", "")).andExpect(status().isBadRequest());
        create(ownerToken, employee().put("birthDate", "2999-01-01")).andExpect(status().isBadRequest());
        create(ownerToken, employee().put("phoneNumber", "")).andExpect(status().isBadRequest());
        create(ownerToken, employee().set("roleIds", objectMapper.createArrayNode())).andExpect(status().isBadRequest());
        create(ownerToken, employee().set("branchIds", objectMapper.createArrayNode())).andExpect(status().isBadRequest());
        assertThat(ids(call(HttpMethod.GET, "/employees", ownerToken))).isEmpty();
    }

    @Test
    @DisplayName("El RFC, el correo y la CURP no se repiten dentro de la empresa")
    void rfcEmailAndCurpAreUniqueInsideTheCompany() throws Exception {
        ObjectNode first = employee();
        create(ownerToken, first).andExpect(status().isCreated());
        create(ownerToken, employee().put("rfc", first.get("rfc").asText())).andExpect(status().isConflict());
        create(ownerToken, employee().put("email", first.get("email").asText())).andExpect(status().isConflict());
        create(ownerToken, employee().put("curp", first.get("curp").asText())).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Si el correo o el RFC ya son de otra cuenta, se rechaza sin decir cuál; si son de la misma persona, se admite")
    void dataThatBelongsToAnotherAccountIsRejectedWithoutSayingWhich() throws Exception {
        String rfc = rfc();
        String email = "existente-" + RestWorld.next() + "@example.com";
        registerAccount(email, rfc);

        // El mismo correo con otro RFC, y el mismo RFC con otro correo: choques con una cuenta ajena.
        String mismatchByEmail = create(ownerToken, employee().put("email", email)).andExpect(status().isConflict())
            .andReturn().getResponse().getContentAsString();
        String mismatchByRfc = create(ownerToken, employee().put("rfc", rfc)).andExpect(status().isConflict())
            .andReturn().getResponse().getContentAsString();
        assertThat(mismatchByEmail).doesNotContainIgnoringCase("rfc").doesNotContain(email);
        assertThat(mismatchByRfc).doesNotContainIgnoringCase("correo").doesNotContain(rfc);

        // La misma persona (mismo correo y mismo RFC) ya tiene cuenta: se admite, su acceso será esa cuenta.
        create(ownerToken, employee().put("email", email).put("rfc", rfc)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("La persona dueña de la cuenta no se registra como empleada de su propia empresa")
    void theAccountOwnerIsNotRegisteredAsItsOwnEmployee() throws Exception {
        String rfc = rfc();
        String email = "duena-" + RestWorld.next() + "@example.com";
        String accountId = registerAccount(email, rfc);
        String account = login(email);
        String ownCompany = world.createCompany(accountId, account);
        String token = world.enter(accountId, ownCompany, account);
        String ownBranch = world.createBranch(ownCompany, token, "MATRIZ");
        ObjectNode body = employee().put("email", email).put("rfc", rfc);
        body.set("branchIds", objectMapper.createArrayNode().add(ownBranch));

        send(HttpMethod.POST, "/employees", token, body.toString(), UUID.randomUUID().toString(), ownCompany)
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Los roles y las sucursales deben ser de la empresa, estar activos y poder darse por quien registra")
    void rolesAndBranchesMustBeValidForTheCompanyAndDelegableByTheActor() throws Exception {
        create(ownerToken, employee().set("roleIds", ids("rol_inexistente"))).andExpect(status().isUnprocessableEntity());
        create(ownerToken, employee().set("branchIds", ids("brn_inexistente"))).andExpect(status().isUnprocessableEntity());
        // Ni siquiera la persona dueña puede dar el rol de dueño: tendría el acceso total de la cuenta.
        create(ownerToken, employee().set("roleIds", ids("rol_sys_account_owner"))).andExpect(status().isUnprocessableEntity());

        RestWorld.Person other = world.register();
        String otherAccount = world.login(other.email());
        String otherCompany = world.createCompany(other.accountId(), otherAccount);
        String otherToken = world.enter(other.accountId(), otherCompany, otherAccount);
        String foreignBranch = world.createBranch(otherCompany, otherToken, "MATRIZ");
        create(ownerToken, employee().set("branchIds", ids(foreignBranch))).andExpect(status().isUnprocessableEntity());

        // Un rol desactivado ya no se puede dar.
        String roleId = customRole("Cajero RH", "[\"sales.read\"]");
        send(HttpMethod.POST, "/roles/" + roleId + "/deactivation", ownerToken, null, UUID.randomUUID().toString())
            .andExpect(status().isOk());
        create(ownerToken, employee().set("roleIds", ids(roleId))).andExpect(status().isUnprocessableEntity());

        // Quien registra no puede dar un rol con permisos que no tiene.
        jdbc.update("""
            INSERT INTO company_management.role
              (id, public_role_id, public_company_id, name, is_system_role, status, permissions_json, created_at, updated_at, version)
            VALUES (?, ?, ?, 'SOLO_RH', FALSE, 'ACTIVE', '["employees.*"]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """, UUID.randomUUID(), "rol_" + UUID.randomUUID(), company);
        RestWorld.Person hr = world.register();
        world.addMember(company, hr.accountId(), "ACTIVE", "[\"SOLO_RH\"]");
        String hrToken = world.enter(hr.accountId(), company, world.login(hr.email()));
        create(hrToken, employee()).andExpect(status().isUnprocessableEntity());
        create(ownerToken, employee()).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Mientras está pendiente se pueden corregir sus datos; una vez activo, no")
    void aPendingProfileCanBeCorrectedButAnActiveOneCannot() throws Exception {
        ObjectNode body = employee();
        String id = objectMapper.readTree(create(ownerToken, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
            .get("employeeId").asText();

        ObjectNode fix = body.deepCopy().put("phoneNumber", "3312345678");
        send(HttpMethod.PATCH, "/employees/" + id, ownerToken, fix.toString(), null).andExpect(status().isOk())
            .andExpect(jsonPath("$.phoneNumber").value("3312345678"))
            .andExpect(jsonPath("$.version").value(1));

        ObjectNode other = employee();
        create(ownerToken, other).andExpect(status().isCreated());
        send(HttpMethod.PATCH, "/employees/" + id, ownerToken, fix.deepCopy().put("rfc", other.get("rfc").asText()).toString(), null)
            .andExpect(status().isConflict());

        jdbc.update("UPDATE company_management.employee_profile SET status='ACTIVE' WHERE public_employee_id=?", id);
        send(HttpMethod.PATCH, "/employees/" + id, ownerToken, fix.toString(), null).andExpect(status().isUnprocessableEntity());
        call(HttpMethod.GET, "/employees/emp_no_existe", ownerToken).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("El RFC y la CURP no salen en ningún evento ni en la lista de membresías, y el perfil de otra empresa no se ve")
    void rfcAndCurpNeverLeakAndAnotherCompanysProfileIsHidden() throws Exception {
        ObjectNode body = employee();
        String id = objectMapper.readTree(create(ownerToken, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
            .get("employeeId").asText();

        List<String> payloads = jdbc.queryForList("SELECT payload_json FROM company_management.outbox_event WHERE company_id=?", String.class, company);
        assertThat(String.join("\n", payloads)).doesNotContain(body.get("rfc").asText()).doesNotContain(body.get("curp").asText())
            .doesNotContain(body.get("email").asText());
        String memberships = call(HttpMethod.GET, "/memberships", ownerToken).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(memberships).doesNotContain(body.get("rfc").asText()).doesNotContain(body.get("curp").asText());

        RestWorld.Person other = world.register();
        String otherAccount = world.login(other.email());
        String otherCompany = world.createCompany(other.accountId(), otherAccount);
        String otherToken = world.enter(other.accountId(), otherCompany, otherAccount);
        send(HttpMethod.GET, "/employees/" + id, otherToken, null, null, otherCompany).andExpect(status().isNotFound());
        assertThat(ids(send(HttpMethod.GET, "/employees", otherToken, null, null, otherCompany))).isEmpty();
    }

    @Test
    @DisplayName("La lista se pagina con un cursor y no repite ni pierde a nadie")
    void thePagesFollowACursor() throws Exception {
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            expected.add(objectMapper.readTree(create(ownerToken, employee()).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString()).get("employeeId").asText());
        }
        List<String> seen = new ArrayList<>();
        String after = null;
        int pages = 0;
        do {
            JsonNode page = objectMapper.readTree(call(HttpMethod.GET, "/employees?limit=2" + (after == null ? "" : "&after=" + after), ownerToken)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            page.get("items").forEach(item -> seen.add(item.get("employeeId").asText()));
            after = page.get("page").hasNonNull("nextCursor") ? page.get("page").get("nextCursor").asText() : null;
            pages++;
        } while (after != null && pages < 5);

        assertThat(pages).isEqualTo(2);
        assertThat(seen).containsExactlyInAnyOrderElementsOf(expected);
        call(HttpMethod.GET, "/employees?limit=0", ownerToken).andExpect(status().isBadRequest());
        call(HttpMethod.GET, "/employees?status=NOPE", ownerToken).andExpect(status().isBadRequest());
    }

    // ---- Ayudas ----

    /** Un empleado nuevo y válido con datos que no se repiten, con el rol STAFF y la sucursal de la prueba. */
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

    private String customRole(String name, String permissionsJson) throws Exception {
        String codes = permissionsJson.substring(1, permissionsJson.length() - 1);
        String body = "{\"name\":\"" + name + "\",\"permissions\":[{\"permissionCode\":" + codes + "}]}";
        String created = send(HttpMethod.POST, "/roles", ownerToken, body, UUID.randomUUID().toString())
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

    private String login(String email) throws Exception {
        return world.login(email);
    }

    private ResultActions create(String bearer, ObjectNode body) throws Exception {
        return send(HttpMethod.POST, "/employees", bearer, body.toString(), UUID.randomUUID().toString());
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

    private List<String> ids(ResultActions result) throws Exception {
        JsonNode page = objectMapper.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("employeeId").asText()));
        return ids;
    }
}
