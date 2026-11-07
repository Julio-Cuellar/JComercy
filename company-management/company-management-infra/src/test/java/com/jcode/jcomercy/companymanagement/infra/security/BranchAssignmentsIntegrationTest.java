package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M6 del frente de roles: las sucursales de una persona. Listar, agregar, suspender y quitar. Los roles son de la
 * persona, no de cada sucursal. Una persona activa no se queda sin su última sucursal activa: para cortarle el acceso
 * se suspende su membresía. Lo que se quita no se borra: queda guardado como retirado.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BranchAssignmentsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    private RestWorld world;
    private String company;
    private String ownerToken;
    private String branchA;
    private String branchB;
    private RestWorld.Person ana;
    private String membership;

    @BeforeEach
    void company() throws Exception {
        world = new RestWorld(mockMvc, objectMapper, jdbc);
        RestWorld.Person owner = world.register();
        String account = world.login(owner.email());
        company = world.createCompany(owner.accountId(), account);
        ownerToken = world.enter(owner.accountId(), company, account);
        branchA = world.createBranch(company, ownerToken, "MATRIZ");
        branchB = world.createBranch(company, ownerToken, "SUCURSAL");
        ana = world.register();
        world.addMember(company, ana.accountId(), "ACTIVE", "[\"STAFF\"]");
        world.assignBranch(company, ana.accountId(), branchA);
        membership = jdbc.queryForObject("SELECT public_membership_id FROM company_management.company_membership WHERE public_company_id=? AND user_id=?",
            String.class, company, ana.accountId());
    }

    @Test
    @DisplayName("Se listan las sucursales de la persona con su estado y los roles que tiene")
    void theBranchesOfAPersonAreListed() throws Exception {
        call(HttpMethod.GET, "", ownerToken).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].assignmentId").value(org.hamcrest.Matchers.startsWith("asg_")))
            .andExpect(jsonPath("$.items[0].membershipId").value(membership))
            .andExpect(jsonPath("$.items[0].branchId").value(branchA))
            .andExpect(jsonPath("$.items[0].roleIds[0]").value("rol_sys_staff"))
            .andExpect(jsonPath("$.items[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Agregar una sucursal la suma al token de la persona, y repetir la clave de idempotencia no la duplica")
    void addingABranchAddsItToThePersonsTokenAndRepeatingTheKeyDoesNotDuplicate() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = send(HttpMethod.POST, "", ownerToken, "{\"branchId\":\"" + branchB + "\"}", key).andExpect(status().isCreated())
            .andExpect(jsonPath("$.branchId").value(branchB))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(header().exists("Location"))
            .andReturn().getResponse().getContentAsString();
        String replay = send(HttpMethod.POST, "", ownerToken, "{\"branchId\":\"" + branchB + "\"}", key).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(replay).get("assignmentId").asText()).isEqualTo(objectMapper.readTree(first).get("assignmentId").asText());
        call(HttpMethod.GET, "", ownerToken).andExpect(jsonPath("$.items.length()").value(2));
        assertThat(branchesInTokenOf(ana)).containsExactlyInAnyOrder(branchA, branchB);
    }

    @Test
    @DisplayName("No se agrega una sucursal que no existe o es de otra empresa, ni una repetida, ni a una membresía ajena o revocada")
    void invalidAssignmentsAreRejected() throws Exception {
        create("brn_inexistente").andExpect(status().isUnprocessableEntity());

        RestWorld.Person other = world.register();
        String otherAccount = world.login(other.email());
        String otherCompany = world.createCompany(other.accountId(), otherAccount);
        String otherToken = world.enter(other.accountId(), otherCompany, otherAccount);
        String foreignBranch = world.createBranch(otherCompany, otherToken, "MATRIZ");
        create(foreignBranch).andExpect(status().isUnprocessableEntity());

        create(branchA).andExpect(status().isConflict());
        send(HttpMethod.POST, "/api/v1/companies/" + otherCompany + "/memberships/" + membership + "/assignments", otherToken,
            "{\"branchId\":\"" + foreignBranch + "\"}", UUID.randomUUID().toString()).andExpect(status().isNotFound());

        jdbc.update("UPDATE company_management.company_membership SET status='REVOKED' WHERE public_membership_id=?", membership);
        create(branchB).andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Los roles son de la persona: pedir otros por sucursal, o permisos sueltos, se rechaza explicándolo")
    void rolesAndPermissionsPerBranchAreRejected() throws Exception {
        send(HttpMethod.POST, "", ownerToken, "{\"branchId\":\"" + branchB + "\",\"roleIds\":[\"rol_sys_branch_manager\"]}", UUID.randomUUID().toString())
            .andExpect(status().isUnprocessableEntity());
        send(HttpMethod.POST, "", ownerToken, "{\"branchId\":\"" + branchB + "\",\"permissions\":[\"sales.create\"]}", UUID.randomUUID().toString())
            .andExpect(status().isUnprocessableEntity());
        send(HttpMethod.POST, "", ownerToken, "{\"branchId\":\"" + branchB + "\",\"roleIds\":[\"rol_sys_staff\"]}", UUID.randomUUID().toString())
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Suspender una sucursal la quita del token y reactivarla la devuelve; una versión vieja da conflicto")
    void suspendingABranchTakesItOutOfTheTokenAndReactivatingBringsItBack() throws Exception {
        String assignment = createdId(branchB);

        send(HttpMethod.PATCH, "/" + assignment, ownerToken, "{\"branchId\":\"" + branchB + "\",\"status\":\"SUSPENDED\"}", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUSPENDED"))
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(header().string("ETag", "\"1\""));
        assertThat(branchesInTokenOf(ana)).containsExactly(branchA);

        send(HttpMethod.PATCH, "/" + assignment, ownerToken, "{\"status\":\"ACTIVE\"}", null, "\"0\"").andExpect(status().isConflict());
        send(HttpMethod.PATCH, "/" + assignment, ownerToken, "{\"status\":\"ACTIVE\"}", null, "\"1\"").andExpect(status().isOk());
        assertThat(branchesInTokenOf(ana)).containsExactlyInAnyOrder(branchA, branchB);

        send(HttpMethod.PATCH, "/" + assignment, ownerToken, "{\"branchId\":\"" + branchA + "\",\"status\":\"ACTIVE\"}", null)
            .andExpect(status().isUnprocessableEntity());
        send(HttpMethod.PATCH, "/" + assignment, ownerToken, "{\"status\":\"NOPE\"}", null).andExpect(status().isBadRequest());
        send(HttpMethod.PATCH, "/" + assignment, ownerToken, "{}", null).andExpect(status().isBadRequest());
        send(HttpMethod.PATCH, "/asg_no_existe", ownerToken, "{\"status\":\"ACTIVE\"}", null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Una persona activa no se queda sin su última sucursal activa, pero una suspendida o revocada sí puede quedarse sin ellas")
    void thePersonKeepsAtLeastOneActiveBranchWhileActive() throws Exception {
        String assignmentA = jdbc.queryForObject("SELECT public_assignment_id FROM company_management.branch_assignment WHERE public_membership_id=?", String.class, membership);

        send(HttpMethod.PATCH, "/" + assignmentA, ownerToken, "{\"status\":\"SUSPENDED\"}", null).andExpect(status().isConflict());
        remove(assignmentA).andExpect(status().isConflict());

        String assignmentB = createdId(branchB);
        send(HttpMethod.PATCH, "/" + assignmentA, ownerToken, "{\"status\":\"SUSPENDED\"}", null).andExpect(status().isOk());
        remove(assignmentB).andExpect(status().isConflict());

        jdbc.update("UPDATE company_management.company_membership SET status='SUSPENDED' WHERE public_membership_id=?", membership);
        remove(assignmentB).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Quitar una sucursal la retira de la lista, del detalle y del filtro, y se puede volver a agregar")
    void removingABranchHidesItEverywhereAndItCanBeAddedAgain() throws Exception {
        String assignmentB = createdId(branchB);

        send(HttpMethod.DELETE, "/" + assignmentB, ownerToken, null, null).andExpect(status().isBadRequest());
        remove(assignmentB).andExpect(status().isNoContent());

        call(HttpMethod.GET, "", ownerToken).andExpect(jsonPath("$.items.length()").value(1));
        assertThat(branchesInTokenOf(ana)).containsExactly(branchA);
        mockMvc.perform(request(HttpMethod.GET, "/api/v1/companies/" + company + "/memberships/" + membership).header("Authorization", ownerToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.assignments.length()").value(1));
        mockMvc.perform(request(HttpMethod.GET, "/api/v1/companies/" + company + "/memberships?branchId=" + branchB).header("Authorization", ownerToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        send(HttpMethod.PATCH, "/" + assignmentB, ownerToken, "{\"status\":\"ACTIVE\"}", null).andExpect(status().isNotFound());
        remove(assignmentB).andExpect(status().isNotFound());

        create(branchB).andExpect(status().isCreated());
        assertThat(branchesInTokenOf(ana)).containsExactlyInAnyOrder(branchA, branchB);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_management.branch_assignment WHERE public_membership_id=? AND public_branch_id=?",
            Integer.class, membership, branchB)).as("se reutiliza la fila retirada, no se duplica").isEqualTo(1);
    }

    @Test
    @DisplayName("El personal sin permiso no ve ni cambia asignaciones; la administración de la empresa sí")
    void staffCannotAndAdminsCan() throws Exception {
        String anaToken = world.enter(ana.accountId(), company, world.login(ana.email()));
        call(HttpMethod.GET, "", anaToken).andExpect(status().isForbidden());
        create(anaToken, branchB).andExpect(status().isForbidden());

        RestWorld.Person admin = world.register();
        world.addMember(company, admin.accountId(), "ACTIVE", "[\"COMPANY_ADMIN\"]");
        String adminToken = world.enter(admin.accountId(), company, world.login(admin.email()));
        call(HttpMethod.GET, "", adminToken).andExpect(status().isOk());
        create(adminToken, branchB).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Agregar, cambiar y quitar sucursales deja sus eventos, sin datos personales")
    void assignmentEventsAreRecorded() throws Exception {
        String assignmentB = createdId(branchB);
        send(HttpMethod.PATCH, "/" + assignmentB, ownerToken, "{\"status\":\"SUSPENDED\"}", null).andExpect(status().isOk());
        remove(assignmentB).andExpect(status().isNoContent());

        List<String> types = jdbc.queryForList("SELECT event_type FROM company_management.outbox_event WHERE company_id=?", String.class, company);
        assertThat(types).contains("branch-assignment.created", "branch-assignment.updated", "branch-assignment.revoked");
        String everything = String.join("\n", jdbc.queryForList("SELECT payload_json FROM company_management.outbox_event WHERE company_id=?", String.class, company));
        assertThat(everything).doesNotContain(ana.email());
    }

    // ---- Ayudas ----

    private ResultActions create(String branchId) throws Exception {
        return create(ownerToken, branchId);
    }

    private ResultActions create(String bearer, String branchId) throws Exception {
        return send(HttpMethod.POST, "", bearer, "{\"branchId\":\"" + branchId + "\"}", UUID.randomUUID().toString());
    }

    private String createdId(String branchId) throws Exception {
        String body = create(branchId).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("assignmentId").asText();
    }

    private ResultActions remove(String assignmentId) throws Exception {
        return send(HttpMethod.DELETE, "/" + assignmentId, ownerToken, null, UUID.randomUUID().toString());
    }

    private List<String> branchesInTokenOf(RestWorld.Person person) throws Exception {
        JsonNode claims = world.claimsOf(world.enter(person.accountId(), company, world.login(person.email())));
        List<String> branches = new ArrayList<>();
        claims.get("branchIds").forEach(node -> branches.add(node.asText()));
        return branches;
    }

    private ResultActions call(HttpMethod method, String path, String bearer) throws Exception {
        return send(method, path, bearer, null, null);
    }

    private ResultActions send(HttpMethod method, String path, String bearer, String body, String idempotencyKey) throws Exception {
        return send(method, path, bearer, body, idempotencyKey, null);
    }

    private ResultActions send(HttpMethod method, String path, String bearer, String body, String idempotencyKey, String ifMatch) throws Exception {
        String url = path.startsWith("/api/") ? path : "/api/v1/companies/" + company + "/memberships/" + membership + "/assignments" + path;
        MockHttpServletRequestBuilder builder = request(method, url).header("Authorization", bearer);
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        if (ifMatch != null) {
            builder.header("If-Match", ifMatch);
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(builder);
    }
}
