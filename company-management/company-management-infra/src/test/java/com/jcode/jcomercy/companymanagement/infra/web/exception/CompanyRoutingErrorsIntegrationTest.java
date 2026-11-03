package com.jcode.jcomercy.companymanagement.infra.web.exception;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ERR-003: los errores de enrutamiento de Spring no deben convertirse en un 500. */
@SpringBootTest
@AutoConfigureMockMvc
class CompanyRoutingErrorsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Una ruta que no existe responde 404 y no 500")
    void unknownRouteRespondsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/ruta-que-no-existe").header("Authorization", "Bearer " + token()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.detail").value("La dirección solicitada no existe. Revisa que esté bien escrita."));
    }

    @Test
    @DisplayName("Un método no permitido en una ruta existente responde 405 con la cabecera Allow y no 500")
    void unsupportedMethodRespondsMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login").header("Authorization", "Bearer " + token()))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", "POST"))
            .andExpect(jsonPath("$.status").value(405))
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    private static String token() {
        SecretKey key = Keys.hmacShaKeyFor("local-only-change-me-012345678901234567890123456789".getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
            .subject("user-routing-err-200")
            .issuer("jcomercy")
            .issuedAt(now)
            .expiration(new Date(now.getTime() + 300_000))
            .signWith(key)
            .compact();
    }
}
