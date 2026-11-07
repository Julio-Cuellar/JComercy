package com.jcode.jcomercy.sales.infra;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El módulo de ventas arranca solo, con su esquema propio, y protege todo con el token de empresa que emite
 * company-management: sin token o con uno falsificado no se entra, y una dirección que no existe responde 404 y no 500
 * (ERR-003 en los otros módulos).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SalesApplicationSmokeIntegrationTest {

    private static final String COMPANY = "cmp_smoke_100";
    private static final String SECRET = "local-only-change-me-012345678901234567890123456789";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("El módulo arranca y crea su esquema propio, sales")
    void theModuleStartsWithItsOwnSchema() {
        Integer schemas = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.schemata WHERE UPPER(schema_name) = 'SALES'", Integer.class);

        // H2 los cuenta dos veces: Flyway crea "sales" entre comillas y la migración V001 crea SALES sin ellas. En
        // PostgreSQL es uno solo. Lo que importa es que exista.
        assertThat(schemas).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Sin token no se entra")
    void withoutATokenTheRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/companies/{c}/prices", COMPANY)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un token firmado con otra llave no se acepta")
    void aForgedTokenIsRejected() throws Exception {
        SecretKey other = Keys.hmacShaKeyFor("otra-llave-que-no-es-la-de-la-aplicacion-0123456789".getBytes(StandardCharsets.UTF_8));
        String forged = "Bearer " + Jwts.builder().subject("intruso").issuer("jcomercy")
            .claim("companyIds", List.of(COMPANY)).claim("permissions", "*")
            .expiration(new Date(System.currentTimeMillis() + 3600_000)).signWith(other).compact();

        mockMvc.perform(get("/api/v1/companies/{c}/prices", COMPANY).header("Authorization", forged))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Una dirección que no existe responde 404 y no 500")
    void anUnknownRouteRespondsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/companies/{c}/tickets-que-no-existen", COMPANY).header("Authorization", validToken()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("La dirección solicitada no existe. Revisa que esté bien escrita."));
    }

    private static String validToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder().subject("usuario-smoke-100").issuer("jcomercy")
            .claim("companyIds", List.of(COMPANY)).claim("branchIds", List.of("br_smoke_100"))
            .claim("permissions", "sales.read")
            .expiration(new Date(System.currentTimeMillis() + 3600_000)).signWith(key).compact();
    }
}
