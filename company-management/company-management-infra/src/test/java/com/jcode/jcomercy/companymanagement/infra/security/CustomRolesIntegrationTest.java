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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M2 del frente de roles: crear, editar, desactivar y borrar los roles propios de una empresa. Los del sistema no se
 * tocan; nadie da un permiso que no tiene; un rol que alguien usa no se borra.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CustomRolesIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    private RestWorld world;
    private String company;
    private String ownerToken;

    @BeforeEach
    void company() throws Exception {
        world = new RestWorld(mockMvc, objectMapper, jdbc);
        RestWorld.Person owner = world.register();
        String account = world.login(owner.email());
        company = world.createCompany(owner.accountId(), account);
        ownerToken = world.enter(owner.accountId(), company, account);
    }

    @Test
    @DisplayName("Se crea un rol propio con sus permisos y aparece en la lista de roles propios")
    void aCustomRoleIsCreatedAndListed() throws Exception {
        String body = create(ownerToken, "Cajero", "[\"sales.create\",\"sales.read\"]").andExpect(status().isCreated())
            .andExpect(jsonPath("$.roleId").value(org.hamcrest.Matchers.startsWith("rol_")))
            .andExpect(jsonPath("$.name").value("Cajero"))
            .andExpect(jsonPath("$.type").value("CUSTOM"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.permissions[0].permissionCode").value("sales.create"))
            .andExpect(jsonPath("$.permissions[1].permissionCode").value("sales.read"))
            .andExpect(header().exists("Location"))
            .andReturn().getResponse().getContentAsString();
        String roleId = objectMapper.readTree(body).get("roleId").asText();

        assertThat(roleIds(call(HttpMethod.GET, "/roles?type=CUSTOM", ownerToken, null, null))).containsExactly(roleId);
    }

    @Test
    @DisplayName("Crear exige la clave de idempotencia y repetirla no crea otro rol")
    void creatingNeedsTheIdempotencyKeyAndRepeatingItDoesNotCreateAnother() throws Exception {
        mockMvc.perform(request(HttpMethod.POST, "/api/v1/companies/" + company + "/roles")
                .header("Authorization", ownerToken).contentType(MediaType.APPLICATION_JSON)
                .content(roleBody("Sin clave", "[\"sales.read\"]")))
            .andExpect(status().isBadRequest());

        String key = UUID.randomUUID().toString();
        String first = send(HttpMethod.POST, "/roles", ownerToken, roleBody("Con clave", "[\"sales.read\"]"), key, null)
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String replay = send(HttpMethod.POST, "/roles", ownerToken, roleBody("Con clave", "[\"sales.read\"]"), key, null)
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(replay).get("roleId").asText()).as("repetir la clave devuelve el mismo rol")
            .isEqualTo(objectMapper.readTree(first).get("roleId").asText());
        assertThat(roleIds(call(HttpMethod.GET, "/roles?type=CUSTOM", ownerToken, null, null))).hasSize(1);
    }

    @Test
    @DisplayName("El nombre no se repite en la empresa ni se parece al de un rol del sistema, pero otra empresa sí puede usarlo")
    void theNameIsUniqueInTheCompanyAndNeverASystemRoleName() throws Exception {
        create(ownerToken, "Cajero", "[\"sales.read\"]").andExpect(status().isCreated());
        create(ownerToken, "cajero", "[\"sales.read\"]").andExpect(status().isConflict());
        create(ownerToken, "Staff", "[\"sales.read\"]").andExpect(status().isConflict());
        create(ownerToken, "COMPANY_ADMIN", "[\"sales.read\"]").andExpect(status().isConflict());

        RestWorld.Person other = world.register();
        String otherAccount = world.login(other.email());
        String otherCompany = world.createCompany(other.accountId(), otherAccount);
        String otherToken = world.enter(other.accountId(), otherCompany, otherAccount);
        send(HttpMethod.POST, "/roles", otherToken, roleBody("Cajero", "[\"sales.read\"]"), UUID.randomUUID().toString(), otherCompany)
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Los datos mal formados se rechazan con la razón y el comodín total no se puede delegar")
    void malformedDataIsRejectedAndTheTotalWildcardCannotBeGiven() throws Exception {
        create(ownerToken, "  ", "[\"sales.read\"]").andExpect(status().isBadRequest());
        create(ownerToken, "x".repeat(161), "[\"sales.read\"]").andExpect(status().isBadRequest());
        create(ownerToken, "Vacío", "[]").andExpect(status().isBadRequest());
        create(ownerToken, "Mal permiso", "[\"Ventas Crear\"]").andExpect(status().isBadRequest());
        create(ownerToken, "Todo", "[\"*\"]").andExpect(status().isUnprocessableEntity());
        send(HttpMethod.POST, "/roles", ownerToken, """
            {"name":"Por sucursal","permissions":[{"permissionCode":"sales.read","branchIds":["brn_1"]}]}
            """, UUID.randomUUID().toString(), null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Nadie da un permiso que no tiene: quien administra roles no puede dar los de ventas si no los tiene")
    void nobodyGivesAPermissionTheyDoNotHave() throws Exception {
        String manager = memberWithRole("GESTOR_DE_ROLES", "[\"roles.*\"]");

        create(manager, "Con ventas", "[\"sales.create\"]").andExpect(status().isUnprocessableEntity());
        create(manager, "Con roles", "[\"roles.read\"]").andExpect(status().isCreated());
        create(ownerToken, "Con ventas", "[\"sales.create\"]").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Un empleado sin permiso de roles no puede crearlos")
    void anEmployeeWithoutRolePermissionCannotCreate() throws Exception {
        RestWorld.Person ana = world.register();
        world.addMember(company, ana.accountId(), "ACTIVE", "[\"STAFF\"]");
        String anaToken = world.enter(ana.accountId(), company, world.login(ana.email()));

        create(anaToken, "Intruso", "[\"sales.read\"]").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Editar cambia los permisos y la versión; el nombre, los roles del sistema y los de otra empresa no se tocan")
    void updatingChangesThePermissionsButNotTheNameNorSystemOrForeignRoles() throws Exception {
        String roleId = createdId(ownerToken, "Cajero", "[\"sales.read\"]");

        send(HttpMethod.PATCH, "/roles/" + roleId, ownerToken, roleBody("Cajero", "[\"sales.read\",\"sales.cancel\"]"), null, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions.length()").value(2))
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(header().string("ETag", "\"1\""));
        send(HttpMethod.PATCH, "/roles/" + roleId, ownerToken, roleBody("Otro nombre", "[\"sales.read\"]"), null, null)
            .andExpect(status().isUnprocessableEntity());
        send(HttpMethod.PATCH, "/roles/rol_sys_staff", ownerToken, roleBody("STAFF", "[\"sales.read\"]"), null, null)
            .andExpect(status().isUnprocessableEntity());

        RestWorld.Person other = world.register();
        String otherAccount = world.login(other.email());
        String otherCompany = world.createCompany(other.accountId(), otherAccount);
        String otherToken = world.enter(other.accountId(), otherCompany, otherAccount);
        send(HttpMethod.PATCH, "/roles/" + roleId, otherToken, roleBody("Cajero", "[\"sales.read\"]"), null, otherCompany)
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Si el rol cambió desde que se consultó, editarlo con la versión vieja da conflicto")
    void editingWithAStaleVersionIsAConflict() throws Exception {
        String roleId = createdId(ownerToken, "Cajero", "[\"sales.read\"]");
        send(HttpMethod.PATCH, "/roles/" + roleId, ownerToken, roleBody("Cajero", "[\"sales.cancel\"]"), null, null).andExpect(status().isOk());

        send(HttpMethod.PATCH, "/roles/" + roleId, ownerToken, roleBody("Cajero", "[\"sales.read\"]"), null, null, "\"0\"")
            .andExpect(status().isConflict());
        send(HttpMethod.PATCH, "/roles/" + roleId, ownerToken, roleBody("Cajero", "[\"sales.read\"]"), null, null, "\"1\"")
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Editar tampoco permite agregar un permiso que quien edita no tiene")
    void editingCannotAddAPermissionTheEditorLacks() throws Exception {
        String roleId = createdId(ownerToken, "Cajero", "[\"sales.read\"]");
        String manager = memberWithRole("GESTOR_DE_ROLES", "[\"roles.*\"]");

        send(HttpMethod.PATCH, "/roles/" + roleId, manager, roleBody("Cajero", "[\"sales.read\",\"sales.cancel\"]"), null, null)
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Desactivar un rol le quita sus permisos a quien lo tiene en la siguiente petición")
    void deactivatingARoleTakesItsPermissionsAwayRightAway() throws Exception {
        String roleId = createdId(ownerToken, "Lector de equipo", "[\"memberships.read\"]");
        RestWorld.Person ana = world.register();
        world.addMember(company, ana.accountId(), "ACTIVE", "[\"Lector de equipo\"]");
        String anaToken = world.enter(ana.accountId(), company, world.login(ana.email()));
        call(HttpMethod.GET, "/memberships", anaToken, null, null).andExpect(status().isOk());

        send(HttpMethod.POST, "/roles/" + roleId + "/deactivation", ownerToken, null, UUID.randomUUID().toString(), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("INACTIVE"));

        call(HttpMethod.GET, "/memberships", anaToken, null, null).andExpect(status().isForbidden());
        send(HttpMethod.POST, "/roles/rol_sys_staff/deactivation", ownerToken, null, UUID.randomUUID().toString(), null)
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Un rol que alguien usa no se borra; uno sin uso se borra, desaparece y su nombre se puede volver a usar")
    void aRoleInUseIsNotDeletedAndAnUnusedOneIsDeletedAndItsNameCanBeReused() throws Exception {
        String inUse = createdId(ownerToken, "En uso", "[\"sales.read\"]");
        RestWorld.Person ana = world.register();
        world.addMember(company, ana.accountId(), "SUSPENDED", "[\"En uso\"]");
        send(HttpMethod.DELETE, "/roles/" + inUse + "?confirmation=true", ownerToken, null, UUID.randomUUID().toString(), null)
            .andExpect(status().isConflict());

        String unused = createdId(ownerToken, "Sin uso", "[\"sales.read\"]");
        send(HttpMethod.DELETE, "/roles/" + unused + "?confirmation=false", ownerToken, null, UUID.randomUUID().toString(), null)
            .andExpect(status().isBadRequest());
        send(HttpMethod.DELETE, "/roles/" + unused, ownerToken, null, UUID.randomUUID().toString(), null)
            .andExpect(status().isBadRequest());
        send(HttpMethod.DELETE, "/roles/" + unused + "?confirmation=true", ownerToken, null, UUID.randomUUID().toString(), null)
            .andExpect(status().isNoContent());

        call(HttpMethod.GET, "/roles/" + unused, ownerToken, null, null).andExpect(status().isNotFound());
        assertThat(roleIds(call(HttpMethod.GET, "/roles?type=CUSTOM", ownerToken, null, null))).doesNotContain(unused);
        create(ownerToken, "Sin uso", "[\"sales.read\"]").andExpect(status().isCreated());
        send(HttpMethod.DELETE, "/roles/rol_sys_staff?confirmation=true", ownerToken, null, UUID.randomUUID().toString(), null)
            .andExpect(status().isUnprocessableEntity());
    }

    // ---- Ayudas ----

    private ResultActions create(String bearer, String name, String permissionCodesJson) throws Exception {
        return send(HttpMethod.POST, "/roles", bearer, roleBody(name, permissionCodesJson), UUID.randomUUID().toString(), null);
    }

    private String createdId(String bearer, String name, String permissionCodesJson) throws Exception {
        String body = create(bearer, name, permissionCodesJson).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("roleId").asText();
    }

    /** Una persona con membresía activa cuyo único rol es uno propio con esos permisos; devuelve su token de empresa. */
    private String memberWithRole(String name, String permissionsJson) throws Exception {
        jdbc.update("""
            INSERT INTO company_management.role
              (id, public_role_id, public_company_id, name, is_system_role, status, permissions_json, created_at, updated_at, version)
            VALUES (?, ?, ?, ?, FALSE, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """, UUID.randomUUID(), "rol_" + UUID.randomUUID(), company, name, permissionsJson);
        RestWorld.Person person = world.register();
        world.addMember(company, person.accountId(), "ACTIVE", "[\"" + name + "\"]");
        return world.enter(person.accountId(), company, world.login(person.email()));
    }

    /** De ["a","b"] arma {"name":...,"permissions":[{"permissionCode":"a"},{"permissionCode":"b"}]}. */
    private static String roleBody(String name, String permissionCodesJson) {
        String permissions = permissionCodesJson.equals("[]") ? "[]" : "[" + Arrays.stream(
                permissionCodesJson.substring(1, permissionCodesJson.length() - 1).split(","))
            .map(code -> "{\"permissionCode\":" + code + "}").reduce((a, b) -> a + "," + b).orElse("") + "]";
        return "{\"name\":\"" + name + "\",\"permissions\":" + permissions + "}";
    }

    private ResultActions call(HttpMethod method, String path, String bearer, String body, String companyId) throws Exception {
        return send(method, path, bearer, body, null, companyId);
    }

    private ResultActions send(HttpMethod method, String path, String bearer, String body, String idempotencyKey, String companyId) throws Exception {
        return send(method, path, bearer, body, idempotencyKey, companyId, null);
    }

    private ResultActions send(HttpMethod method, String path, String bearer, String body, String idempotencyKey, String companyId, String ifMatch) throws Exception {
        MockHttpServletRequestBuilder builder = request(method, "/api/v1/companies/" + (companyId == null ? company : companyId) + path)
            .header("Authorization", bearer);
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

    private List<String> roleIds(ResultActions result) throws Exception {
        JsonNode page = objectMapper.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("roleId").asText()));
        return ids;
    }
}
