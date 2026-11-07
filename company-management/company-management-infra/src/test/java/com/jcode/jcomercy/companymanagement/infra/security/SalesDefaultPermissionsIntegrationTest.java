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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan de ventas, etapa S1: lo que cada rol del sistema puede hacer con las ventas por defecto. Quien vende puede
 * apartar stock con el permiso angosto {@code inventory.reservations.write}, pero no recibir mercancía. Se mira el
 * token que la persona recibe al entrar a la empresa y se evalúa con la misma regla de comodines que usan los módulos.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SalesDefaultPermissionsIntegrationTest {

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
    @DisplayName("El personal vende y consulta ventas, y aparta stock, pero no cancela, no fija precios ni recibe mercancía")
    void staffSellsAndReservesButCannotCancelPriceOrReceive() throws Exception {
        Set<String> staff = permissionsOfAMemberWith("[\"STAFF\"]");

        assertThat(Permissions.grants(staff, "sales.create")).isTrue();
        assertThat(Permissions.grants(staff, "sales.read")).isTrue();
        assertThat(Permissions.grants(staff, "inventory.reservations.write")).isTrue();
        // Para vender necesita encontrar el producto y el almacén de donde sale.
        assertThat(Permissions.grants(staff, "inventory.products.read")).isTrue();
        assertThat(Permissions.grants(staff, "inventory.warehouses.read")).isTrue();
        assertThat(Permissions.grants(staff, "inventory.stock.read")).isTrue();
        assertThat(Permissions.grants(staff, "sales.cancel")).isFalse();
        assertThat(Permissions.grants(staff, "sales.prices.write")).isFalse();
        assertThat(Permissions.grants(staff, "inventory.stock.write")).isFalse();
        assertThat(Permissions.grants(staff, "inventory.stock.adjust")).isFalse();
    }

    @Test
    @DisplayName("El encargado de sucursal vende, cancela y fija precios, pero sigue sin ajustar el stock")
    void branchManagerSellsCancelsAndSetsPricesButStillCannotAdjustStock() throws Exception {
        Set<String> manager = permissionsOfAMemberWith("[\"BRANCH_MANAGER\"]");

        assertThat(Permissions.grants(manager, "sales.create")).isTrue();
        assertThat(Permissions.grants(manager, "sales.read")).isTrue();
        assertThat(Permissions.grants(manager, "sales.cancel")).isTrue();
        assertThat(Permissions.grants(manager, "sales.prices.write")).isTrue();
        assertThat(Permissions.grants(manager, "inventory.reservations.write")).isTrue();
        assertThat(manager).doesNotContain("*", "sales.*", "inventory.*");
        assertThat(Permissions.grants(manager, "inventory.stock.adjust")).isFalse();
        assertThat(Permissions.grants(manager, "inventory.stock.negative.enable")).isFalse();
    }

    @Test
    @DisplayName("La administración de la empresa tiene todo lo de ventas")
    void companyAdminHasEverythingInSales() throws Exception {
        Set<String> admin = permissionsOfAMemberWith("[\"COMPANY_ADMIN\"]");

        assertThat(Permissions.grants(admin, "sales.create")).isTrue();
        assertThat(Permissions.grants(admin, "sales.read")).isTrue();
        assertThat(Permissions.grants(admin, "sales.cancel")).isTrue();
        assertThat(Permissions.grants(admin, "sales.prices.write")).isTrue();
        assertThat(Permissions.grants(admin, "inventory.reservations.write")).isTrue();
    }

    @Test
    @DisplayName("Los roles conservan lo que tenían de inventario y de la empresa")
    void theRolesKeepWhatTheyHad() throws Exception {
        Set<String> staff = permissionsOfAMemberWith("[\"STAFF\"]");
        assertThat(staff).contains("operational_context.select", "effective_permissions.read_own");

        Set<String> manager = permissionsOfAMemberWith("[\"BRANCH_MANAGER\"]");
        assertThat(manager).contains("inventory.stock.read", "inventory.stock.write", "inventory.warehouses.read",
            "inventory.products.read", "branches.read", "branches.update", "operations.*", "effective_permissions.read_own");

        Set<String> admin = permissionsOfAMemberWith("[\"COMPANY_ADMIN\"]");
        assertThat(admin).contains("companies.*", "branches.*", "memberships.*", "roles.*", "inventory.*", "employees.*",
            "operations.*", "operational_context.select", "assignments.*", "effective_permissions.read_own");
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
}
