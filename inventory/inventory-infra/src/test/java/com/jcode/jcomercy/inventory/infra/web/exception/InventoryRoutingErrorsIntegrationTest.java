package com.jcode.jcomercy.inventory.infra.web.exception;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ERR-003: los errores de enrutamiento de Spring no deben convertirse en un 500. */
@SpringBootTest
@AutoConfigureMockMvc
class InventoryRoutingErrorsIntegrationTest {

    private static final String COMPANY_ID = "cmp_routing_err_100";

    @Autowired private MockMvc mockMvc;

    private String token;

    @BeforeEach
    void setUp() {
        SecretKey key = Keys.hmacShaKeyFor("local-only-change-me-012345678901234567890123456789".getBytes(StandardCharsets.UTF_8));
        token = "Bearer " + Jwts.builder()
                .subject("user-routing-err-100")
                .issuer("jcomercy")
                .claim("companyIds", List.of(COMPANY_ID))
                .claim("permissions", "INVENTORY_WRITE INVENTORY_READ")
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("Una ruta que no existe responde 404 y no 500")
    void unknownRouteRespondsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/companies/{companyId}/stock/reservaciones", COMPANY_ID)
                        .header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("La dirección solicitada no existe. Revisa que esté bien escrita."));
    }

    @Test
    @DisplayName("Un método no permitido en una ruta existente responde 405 y no 500")
    void unsupportedMethodRespondsMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/api/v1/companies/{companyId}/stock/availability", COMPANY_ID)
                        .header("Authorization", token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
