package com.jcode.jcomercy.companymanagement.infra.web.exception;

import com.jcode.jcomercy.companymanagement.application.service.IdempotencyStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Repetir una operación que ya se procesó es un choque con lo ya hecho (409), no un fallo nuestro (500). */
@SpringBootTest
@AutoConfigureMockMvc
class CompanyRepeatedKeyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdempotencyStore idempotencyStore;

    @Test
    @DisplayName("Repetir una Idempotency-Key que otro proceso ya reclamó responde 409 y no 500 (ERR-007)")
    void repeatedKeyAlreadyClaimedRespondsConflict() throws Exception {
        // Imita una operación anterior de otro proceso: la clave está en el almacén durable pero no en la memoria.
        idempotencyStore.claim("account.register", "claimed-before-restart-001");
        String body = """
            {"name":"Abarrotes Lupita","owner":{"firstName":"Lupita","lastName":"Pérez","birthDate":"1990-01-01",
            "rfc":"PEPL900101AB1","phoneNumber":"5512345678","email":"lupita.repetida@example.com","state":"CDMX",
            "address":"Calle 1","password":"clave-segura-1"}}""";

        mockMvc.perform(post("/api/v1/accounts/registration").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "claimed-before-restart-001").content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_ALREADY_USED"))
            .andExpect(jsonPath("$.detail").value(
                "Esta operación ya se procesó. Consulta el recurso para ver el resultado."));
    }
}
