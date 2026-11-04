package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.core.authorization.Permissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parte C: lo que cada rol del sistema puede hacer en inventario por defecto. Un encargado de sucursal recibe y
 * consulta mercancía, pero no ajusta el stock ni lo deja en negativo: eso lo concede cada empresa, con un rol propio.
 * Se mira el token que recibe al entrar a la empresa y se evalúa con la misma regla de comodines que usa inventario.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BranchManagerDefaultPermissionsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private RestWorld world;
    private String company;

    @BeforeEach
    void company() throws Exception {
        world = new RestWorld(mockMvc, objectMapper, jdbc);
        RestWorld.Person owner = world.register();
        company = world.createCompany(owner.accountId(), world.login(owner.email()));
    }

    @Test
    @DisplayName("Un encargado de sucursal recibe y consulta mercancía, pero no ajusta el stock ni lo deja en negativo")
    void branchManagerCanReceiveButNotAdjustNorGoNegative() throws Exception {
        Set<String> granted = permissionsOfAMemberWith("[\"BRANCH_MANAGER\"]");

        assertThat(granted).contains("inventory.stock.read", "inventory.stock.write", "inventory.warehouses.read",
            "inventory.products.read", "branches.read", "branches.update", "operations.*");
        assertThat(granted).doesNotContain("*", "inventory.*", "inventory.stock.*", "inventory.stock.adjust",
            "inventory.stock.negative.enable");
        assertThat(Permissions.grants(granted, "inventory.stock.read")).isTrue();
        assertThat(Permissions.grants(granted, "inventory.stock.write")).isTrue();
        assertThat(Permissions.grants(granted, "inventory.stock.adjust")).isFalse();
        assertThat(Permissions.grants(granted, "inventory.stock.negative.enable")).isFalse();
    }

    @Test
    @DisplayName("La empresa puede devolverle al encargado el permiso de ajustar con un rol propio")
    void aCompanyCanGrantAdjustmentsBackWithItsOwnRole() throws Exception {
        addRole("AJUSTADOR_DE_PRUEBA", company, "[\"inventory.stock.adjust\"]");

        Set<String> granted = permissionsOfAMemberWith("[\"BRANCH_MANAGER\",\"AJUSTADOR_DE_PRUEBA\"]");

        assertThat(Permissions.grants(granted, "inventory.stock.adjust")).isTrue();
        assertThat(Permissions.grants(granted, "inventory.stock.write")).isTrue();
        // Lo que no se concedió sigue sin concederse.
        assertThat(Permissions.grants(granted, "inventory.stock.negative.enable")).isFalse();
    }

    @Test
    @DisplayName("Los otros roles del sistema conservan lo que tenían: el administrador ajusta, el personal solo consulta")
    void theOtherSystemRolesKeepWhatTheyHad() throws Exception {
        Set<String> admin = permissionsOfAMemberWith("[\"COMPANY_ADMIN\"]");
        assertThat(Permissions.grants(admin, "inventory.stock.adjust")).isTrue();
        assertThat(Permissions.grants(admin, "inventory.warehouses.manage")).isTrue();

        Set<String> staff = permissionsOfAMemberWith("[\"STAFF\"]");
        assertThat(Permissions.grants(staff, "inventory.stock.read")).isTrue();
        assertThat(Permissions.grants(staff, "inventory.stock.write")).isFalse();
    }

    // ---- Ayudas ----

    /** Una persona nueva, miembro de la empresa con esos roles: devuelve los permisos del token que recibe al entrar. */
    private Set<String> permissionsOfAMemberWith(String rolesJson) throws Exception {
        RestWorld.Person member = world.register();
        world.addMember(company, member.accountId(), "ACTIVE", rolesJson);
        String token = world.enter(member.accountId(), company, world.login(member.email()));
        String permissions = world.claimsOf(token).get("permissions").asText();
        return new TreeSet<>(Arrays.asList(permissions.split(" ")));
    }

    private void addRole(String name, String ofCompany, String permissionsJson) {
        jdbc.update("""
            INSERT INTO company_management.role
              (id, public_role_id, public_company_id, name, is_system_role, status, permissions_json, created_at, updated_at, version)
            VALUES (?, ?, ?, ?, FALSE, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """, UUID.randomUUID(), "rol_" + UUID.randomUUID(), ofCompany, name, permissionsJson);
    }
}
