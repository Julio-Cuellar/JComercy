package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M1 del frente de roles: ver las membresías de una empresa, sus roles y mis permisos efectivos. Las membresías se
 * insertan por SQL, como en el resto de pruebas de acceso, porque crearlas es de M5. Aquí solo se lee.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MembershipsReadIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    private RestWorld world;
    private RestWorld.Person owner;
    private String company;
    private String ownerToken;

    @BeforeEach
    void company() throws Exception {
        world = new RestWorld(mockMvc, objectMapper, jdbc);
        owner = world.register();
        String account = world.login(owner.email());
        company = world.createCompany(owner.accountId(), account);
        ownerToken = world.enter(owner.accountId(), company, account);
    }

    @Test
    @DisplayName("Quien administra la empresa ve sus membresías con los roles como identificadores y sin ningún dato de acceso")
    void anAdminSeesTheMembershipsWithTheirRolesAndNoCredentials() throws Exception {
        RestWorld.Person ana = world.register();
        world.addMember(company, ana.accountId(), "ACTIVE", "[\"STAFF\"]");

        String body = perform(company, "/memberships", ownerToken).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].userId").value(ana.accountId()))
            .andExpect(jsonPath("$.items[0].companyId").value(company))
            .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$.items[0].roleIds[0]").value("rol_sys_staff"))
            .andExpect(jsonPath("$.page.limit").value(25))
            .andReturn().getResponse().getContentAsString();

        assertThat(body).as("la respuesta no lleva datos de acceso").doesNotContainIgnoringCase("password")
            .doesNotContainIgnoringCase("hash").doesNotContain(ana.email());
    }

    @Test
    @DisplayName("Se filtran por estado, por rol y por sucursal")
    void theyCanBeFilteredByStatusRoleAndBranch() throws Exception {
        String branch = world.createBranch(company, ownerToken, "MATRIZ");
        RestWorld.Person ana = world.register();
        RestWorld.Person beto = world.register();
        RestWorld.Person carla = world.register();
        world.addMember(company, ana.accountId(), "ACTIVE", "[\"STAFF\"]");
        world.addMember(company, beto.accountId(), "SUSPENDED", "[\"BRANCH_MANAGER\"]");
        world.addMember(company, carla.accountId(), "ACTIVE", "[\"BRANCH_MANAGER\"]");
        world.assignBranch(company, carla.accountId(), branch);

        assertThat(users(perform(company, "/memberships?status=SUSPENDED", ownerToken))).containsExactly(beto.accountId());
        assertThat(users(perform(company, "/memberships?roleId=rol_sys_branch_manager", ownerToken)))
            .containsExactlyInAnyOrder(beto.accountId(), carla.accountId());
        assertThat(users(perform(company, "/memberships?branchId=" + branch, ownerToken))).containsExactly(carla.accountId());
        assertThat(users(perform(company, "/memberships?roleId=rol_inexistente", ownerToken))).isEmpty();
        perform(company, "/memberships?status=NOPE", ownerToken).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("La lista se pagina con un cursor y no repite ni pierde a nadie")
    void thePagesFollowACursorWithoutRepeatingAnyone() throws Exception {
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            RestWorld.Person person = world.register();
            world.addMember(company, person.accountId(), "ACTIVE", "[\"STAFF\"]");
            expected.add(person.accountId());
        }

        List<String> seen = new ArrayList<>();
        String after = null;
        int pages = 0;
        do {
            JsonNode page = json(perform(company, "/memberships?limit=2" + (after == null ? "" : "&after=" + after), ownerToken));
            page.get("items").forEach(item -> seen.add(item.get("userId").asText()));
            after = page.get("page").hasNonNull("nextCursor") ? page.get("page").get("nextCursor").asText() : null;
            pages++;
        } while (after != null && pages < 5);

        assertThat(pages).isEqualTo(2);
        assertThat(seen).containsExactlyInAnyOrderElementsOf(expected).doesNotHaveDuplicates();
        perform(company, "/memberships?limit=0", ownerToken).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("El detalle trae las sucursales asignadas y la membresía de otra empresa no se ve")
    void theDetailShowsTheAssignmentsAndAnotherCompanysMembershipIsHidden() throws Exception {
        String branch = world.createBranch(company, ownerToken, "MATRIZ");
        RestWorld.Person ana = world.register();
        world.addMember(company, ana.accountId(), "ACTIVE", "[\"BRANCH_MANAGER\"]");
        world.assignBranch(company, ana.accountId(), branch);
        String membership = membershipOf(company, ana.accountId());

        perform(company, "/memberships/" + membership, ownerToken).andExpect(status().isOk())
            .andExpect(jsonPath("$.membershipId").value(membership))
            .andExpect(jsonPath("$.roleIds[0]").value("rol_sys_branch_manager"))
            .andExpect(jsonPath("$.assignments[0].branchId").value(branch))
            .andExpect(jsonPath("$.assignments[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$.createdAt").exists());

        RestWorld.Person other = world.register();
        String otherAccount = world.login(other.email());
        String otherCompany = world.createCompany(other.accountId(), otherAccount);
        String otherToken = world.enter(other.accountId(), otherCompany, otherAccount);
        perform(otherCompany, "/memberships/" + membership, otherToken).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un empleado sin permiso de equipo no ve las membresías, pero sí sus propios permisos")
    void anEmployeeWithoutTeamPermissionCannotListButSeesOwnPermissions() throws Exception {
        RestWorld.Person ana = world.register();
        world.addMember(company, ana.accountId(), "ACTIVE", "[\"STAFF\"]");
        String anaToken = world.enter(ana.accountId(), company, world.login(ana.email()));

        perform(company, "/memberships", anaToken).andExpect(status().isForbidden());
        perform(company, "/roles", anaToken).andExpect(status().isForbidden());
        perform(company, "/memberships/me/effective-permissions", anaToken).andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(ana.accountId()))
            .andExpect(jsonPath("$.companyId").value(company))
            .andExpect(jsonPath("$.permissions[*].permissionCode", hasItem("operational_context.select")))
            .andExpect(jsonPath("$.calculatedAt").exists());
    }

    @Test
    @DisplayName("La persona dueña de la cuenta ve que puede todo")
    void theOwnerSeesThatEverythingIsAllowed() throws Exception {
        perform(company, "/memberships/me/effective-permissions", ownerToken).andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions[0].permissionCode").value("*"));
    }

    @Test
    @DisplayName("Los roles son los del sistema más los propios de la empresa, nunca los de otra")
    void rolesAreTheSystemOnesPlusTheCompanysOwn() throws Exception {
        String own = addRole("CAJERO_DE_PRUEBA", company, "[\"sales.create\"]");
        RestWorld.Person other = world.register();
        String otherAccount = world.login(other.email());
        String otherCompany = world.createCompany(other.accountId(), otherAccount);
        String foreign = addRole("ROL_AJENO", otherCompany, "[\"sales.create\"]");

        JsonNode all = json(perform(company, "/roles", ownerToken));
        List<String> ids = new ArrayList<>();
        all.get("items").forEach(role -> ids.add(role.get("roleId").asText()));
        assertThat(ids).contains("rol_sys_account_owner", "rol_sys_company_admin", "rol_sys_branch_manager", "rol_sys_staff", own)
            .doesNotContain(foreign);

        assertThat(roleIds(perform(company, "/roles?type=CUSTOM", ownerToken))).containsExactly(own);
        assertThat(roleIds(perform(company, "/roles?type=SYSTEM", ownerToken))).doesNotContain(own).hasSize(4);
        perform(company, "/roles?type=OTRO", ownerToken).andExpect(status().isBadRequest());

        perform(company, "/roles/" + own, ownerToken).andExpect(status().isOk())
            .andExpect(jsonPath("$.roleId").value(own))
            .andExpect(jsonPath("$.type").value("CUSTOM"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.permissions[0].permissionCode").value("sales.create"));
        perform(company, "/roles/rol_sys_staff", ownerToken).andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("SYSTEM"));
        perform(company, "/roles/" + foreign, ownerToken).andExpect(status().isNotFound());
    }

    private ResultActions perform(String companyId, String path, String bearer) throws Exception {
        return mockMvc.perform(get("/api/v1/companies/" + companyId + path).header("Authorization", bearer));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private List<String> users(ResultActions result) throws Exception {
        List<String> users = new ArrayList<>();
        json(result).get("items").forEach(item -> users.add(item.get("userId").asText()));
        return users;
    }

    private List<String> roleIds(ResultActions result) throws Exception {
        List<String> ids = new ArrayList<>();
        json(result).get("items").forEach(item -> ids.add(item.get("roleId").asText()));
        return ids;
    }

    private String membershipOf(String companyId, String userId) {
        return jdbc.queryForObject("SELECT public_membership_id FROM company_management.company_membership WHERE public_company_id=? AND user_id=?",
            String.class, companyId, userId);
    }

    private String addRole(String name, String ofCompany, String permissionsJson) {
        String publicId = "rol_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
            INSERT INTO company_management.role
              (id, public_role_id, public_company_id, name, is_system_role, status, permissions_json, created_at, updated_at, version)
            VALUES (?, ?, ?, ?, FALSE, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """, UUID.randomUUID(), publicId, ofCompany, name, permissionsJson);
        return publicId;
    }
}
