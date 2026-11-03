package com.jcode.jcomercy.companymanagement.infra.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ERR-011: el login entrega un token de cuenta, que solo sabe quién eres, y elegir una empresa entrega un token de
 * empresa, que nombra esa empresa y nada más. Nunca existe un token con las empresas de la persona juntas.
 */
class JwtTokenServiceTest {

    private static final long ONE_HOUR = 3_600_000L;
    private final JwtTokenService tokens = new JwtTokenService("secreto-de-prueba-con-al-menos-32-bytes-0123456789", "jcomercy", ONE_HOUR);

    @Test
    @DisplayName("El token de cuenta lleva solo la identidad: ninguna empresa, sucursal ni permiso")
    void accountTokenCarriesOnlyTheIdentity() {
        Claims claims = tokens.parse(tokens.issueAccountToken("acc_1"));

        assertThat(claims.getSubject()).isEqualTo("acc_1");
        assertThat(claims.get("permissions", String.class)).isEmpty();
        assertThat((List<?>) claims.get("companyIds", List.class)).isEmpty();
        assertThat((List<?>) claims.get("branchIds", List.class)).isEmpty();
        assertThat(claims.get("ctx")).isNull();
    }

    @Test
    @DisplayName("El token de empresa nombra solo esa empresa, con sus sucursales y los permisos de esa empresa")
    @SuppressWarnings("unchecked")
    void companyTokenNamesOnlyThatCompany() {
        Claims claims = tokens.parse(tokens.issueCompanyToken("acc_1", "com_1",
            List.of("branches.read", "inventory.stock.*"), List.of("br_1", "br_2")));

        assertThat(claims.getSubject()).isEqualTo("acc_1");
        assertThat(claims.get("ctx", String.class)).isEqualTo("com_1");
        assertThat((List<String>) claims.get("companyIds", List.class)).containsExactly("com_1");
        assertThat((List<String>) claims.get("branchIds", List.class)).containsExactly("br_1", "br_2");
        assertThat(claims.get("permissions", String.class)).isEqualTo("branches.read inventory.stock.*");
    }

    @Test
    @DisplayName("Los dos tipos de token duran lo que está configurado")
    void bothKindsExpireAtTheConfiguredTime() {
        Claims account = tokens.parse(tokens.issueAccountToken("acc_1"));
        Claims company = tokens.parse(tokens.issueCompanyToken("acc_1", "com_1", List.of(), List.of()));

        assertThat(account.getExpiration().getTime() - account.getIssuedAt().getTime()).isEqualTo(ONE_HOUR);
        assertThat(company.getExpiration().getTime() - company.getIssuedAt().getTime()).isEqualTo(ONE_HOUR);
    }
}
