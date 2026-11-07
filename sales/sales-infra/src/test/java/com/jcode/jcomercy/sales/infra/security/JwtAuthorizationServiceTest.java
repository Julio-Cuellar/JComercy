package com.jcode.jcomercy.sales.infra.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ventas decide con lo que trae el token de empresa: sus empresas, sus sucursales y sus permisos. Los permisos pueden
 * traer comodines ({@code *} para el dueño, {@code sales.*} para la administración), con la misma regla que usan los
 * demás módulos.
 */
class JwtAuthorizationServiceTest {

    private final JwtAuthorizationService authorization = new JwtAuthorizationService();

    @Test
    @DisplayName("Un permiso exacto vale, y uno que no se tiene no")
    void anExactPermissionGrantsOnlyItself() {
        var authentication = authenticationWith("sales.create", "sales.read");

        assertThat(authorization.hasPermission(authentication, "sales.create")).isTrue();
        assertThat(authorization.hasPermission(authentication, "sales.cancel")).isFalse();
    }

    @Test
    @DisplayName("El comodín de familia vale para lo que cuelga de ella, y el * para todo")
    void wildcardsGrantTheirFamily() {
        assertThat(authorization.hasPermission(authenticationWith("sales.*"), "sales.prices.write")).isTrue();
        assertThat(authorization.hasPermission(authenticationWith("sales.*"), "inventory.stock.write")).isFalse();
        assertThat(authorization.hasPermission(authenticationWith("*"), "sales.cancel")).isTrue();
    }

    @Test
    @DisplayName("Sin permisos o sin sesión no se concede nada")
    void noPermissionsGrantNothing() {
        assertThat(authorization.hasPermission(authenticationWith(), "sales.read")).isFalse();
        assertThat(authorization.hasPermission(null, "sales.read")).isFalse();
    }

    @Test
    @DisplayName("Solo se accede a la empresa y a las sucursales que nombra el token")
    void accessIsLimitedToTheCompaniesAndBranchesOfTheToken() {
        var authentication = authenticationWith("sales.read");

        assertThat(authorization.canAccessCompany(authentication, "cmp_a")).isTrue();
        assertThat(authorization.canAccessCompany(authentication, "cmp_b")).isFalse();
        assertThat(authorization.canAccessBranch(authentication, "br_a1")).isTrue();
        assertThat(authorization.canAccessBranch(authentication, "br_b1")).isFalse();
    }

    private static UsernamePasswordAuthenticationToken authenticationWith(String... permissions) {
        var authorities = java.util.Arrays.stream(permissions).map(p -> new SimpleGrantedAuthority("PERM_" + p)).toList();
        var authentication = new UsernamePasswordAuthenticationToken("usuario-ventas", null, authorities);
        authentication.setDetails(new JwtContext("usuario-ventas", List.of("cmp_a"), List.of("br_a1")));
        return authentication;
    }
}
