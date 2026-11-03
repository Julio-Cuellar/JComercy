package com.jcode.jcomercy.inventory.infra.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ERR-010: el token de un inicio de sesión real trae los permisos del rol, y esos pueden ser comodines
 * (`*`, `inventory.*`). Los permisos heredados del primer JWT local siguen valiendo.
 */
class JwtAuthorizationServiceTest {

    private final JwtAuthorizationService authorization = new JwtAuthorizationService();

    @Test
    @DisplayName("El permiso * concede cualquier permiso de inventario")
    void theStarGrantsEveryInventoryPermission() {
        Authentication owner = with("PERM_*");

        assertThat(authorization.hasPermission(owner, "inventory.stock.write")).isTrue();
        assertThat(authorization.hasPermission(owner, "inventory.stock.negative.enable")).isTrue();
        assertThat(authorization.hasPermission(owner, "inventory.warehouses.manage")).isTrue();
    }

    @Test
    @DisplayName("inventory.* concede todo inventario y nada de otras familias")
    void theInventoryFamilyWildcardGrantsOnlyInventory() {
        Authentication admin = with("PERM_inventory.*", "PERM_companies.*");

        assertThat(authorization.hasPermission(admin, "inventory.products.manage")).isTrue();
        assertThat(authorization.hasPermission(admin, "inventory.stock.read")).isTrue();
        assertThat(authorization.hasPermission(admin, "branches.create")).isFalse();
    }

    @Test
    @DisplayName("inventory.stock.* concede el stock pero no los productos")
    void aNarrowFamilyWildcardGrantsOnlyItsFamily() {
        Authentication manager = with("PERM_inventory.stock.*", "PERM_inventory.warehouses.read");

        assertThat(authorization.hasPermission(manager, "inventory.stock.adjust")).isTrue();
        assertThat(authorization.hasPermission(manager, "inventory.warehouses.read")).isTrue();
        assertThat(authorization.hasPermission(manager, "inventory.warehouses.manage")).isFalse();
        assertThat(authorization.hasPermission(manager, "inventory.products.manage")).isFalse();
    }

    @Test
    @DisplayName("Un permiso exacto concede solo ese permiso")
    void anExactPermissionGrantsOnlyItself() {
        Authentication reader = with("PERM_inventory.stock.read");

        assertThat(authorization.hasPermission(reader, "inventory.stock.read")).isTrue();
        assertThat(authorization.hasPermission(reader, "inventory.stock.write")).isFalse();
    }

    @Test
    @DisplayName("Los permisos heredados INVENTORY_WRITE e INVENTORY_READ siguen valiendo")
    void legacyPermissionsStillWork() {
        Authentication writer = with("PERM_INVENTORY_WRITE");
        Authentication reader = with("PERM_INVENTORY_READ");

        assertThat(authorization.hasPermission(writer, "inventory.stock.write")).isTrue();
        assertThat(authorization.hasPermission(writer, "inventory.stock.read")).isTrue();
        assertThat(authorization.hasPermission(reader, "inventory.stock.read")).isTrue();
        assertThat(authorization.hasPermission(reader, "inventory.stock.write")).isFalse();
        assertThat(authorization.hasPermission(writer, "inventory.stock.negative.enable")).isFalse();
    }

    @Test
    @DisplayName("La forma normalizada PERM_INVENTORY_STOCK_WRITE sigue valiendo")
    void theNormalizedFormStillWorks() {
        Authentication writer = with("PERM_INVENTORY_STOCK_WRITE");

        assertThat(authorization.hasPermission(writer, "inventory.stock.write")).isTrue();
        assertThat(authorization.hasPermission(writer, "inventory.stock.read")).isFalse();
    }

    @Test
    @DisplayName("Una autoridad sin el prefijo PERM_ no concede permisos aunque diga *")
    void anAuthorityWithoutThePermissionPrefixGrantsNothing() {
        assertThat(authorization.hasPermission(with("*"), "inventory.stock.read")).isFalse();
        assertThat(authorization.hasPermission(with("ROLE_ADMIN"), "inventory.stock.read")).isFalse();
    }

    @Test
    @DisplayName("Sin permisos, sin autenticación o sin permiso pedido no se concede nada")
    void nothingIsGrantedWithoutPermissions() {
        assertThat(authorization.hasPermission(with(), "inventory.stock.read")).isFalse();
        assertThat(authorization.hasPermission(null, "inventory.stock.read")).isFalse();
        assertThat(authorization.hasPermission(with("PERM_*"), null)).isFalse();
    }

    private static Authentication with(String... authorities) {
        return new UsernamePasswordAuthenticationToken("usuario", null,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }
}
