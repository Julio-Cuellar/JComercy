package com.jcode.jcomercy.companymanagement.infra;

import com.jcode.jcomercy.companymanagement.infra.persistence.entity.OutboxEventJpaEntity;
import com.jcode.jcomercy.companymanagement.infra.persistence.repository.OutboxSpringDataRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@SpringBootTest
@AutoConfigureMockMvc
class AccountIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxSpringDataRepository outboxRepository;

    @Test
    @DisplayName("Debe registrar una cuenta por REST, persistirla y publicar evento outbox transaccional")
    void shouldRegisterAccountAndPublishOutboxEvent() throws Exception {
        String jsonRequest = """
            {
                "name": "Empresa Corporativa JComercy",
                "owner": {
                    "firstName": "Julio",
                    "lastName": "Pérez López",
                    "birthDate": "1985-04-12",
                    "rfc": "PELJ850412ABC",
                    "phoneNumber": "5512345678",
                    "email": "owner@example.com",
                    "password": "OwnerPassword123!",
                    "state": "Ciudad de México",
                    "address": "Av. Reforma 100"
                }
            }
            """;

        String responseJson = mockMvc.perform(post("/api/v1/accounts/registration")
                .header("Idempotency-Key", "idem_test_account_001")
                .header("X-Correlation-Id", "cor_test_001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
            .andExpect(status().isCreated())
            .andExpect(header().exists("ETag"))
            .andExpect(jsonPath("$.publicAccountId").value(org.hamcrest.Matchers.startsWith("acc_")))
            .andExpect(jsonPath("$.name").value("Empresa Corporativa JComercy"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.owner.firstName").value("Julio"))
            .andExpect(jsonPath("$.owner.rfc").value("PELJ850412ABC"))
            .andReturn().getResponse().getContentAsString();

        // Check Outbox Event table in Database
        // La base H2 es compartida entre pruebas: se filtra por la cuenta recién registrada
        // para no depender del orden de ejecución (ver ERR-001 en documentacion/bitacora.md).
        String publicAccountId = com.jayway.jsonpath.JsonPath.read(responseJson, "$.publicAccountId");
        List<OutboxEventJpaEntity> events = outboxRepository.findAll();
        assertThat(events).isNotEmpty();
        OutboxEventJpaEntity createdEvent = events.stream()
            .filter(e -> "account.created".equals(e.getEventType()))
            .filter(e -> e.getPayloadJson().contains(publicAccountId))
            .findFirst()
            .orElseThrow();

        assertThat(createdEvent.getProducerModule()).isEqualTo("company_management");
        assertThat(createdEvent.getSchemaVersion()).isEqualTo("1.0");
        assertThat(createdEvent.getStatus()).isEqualTo("PENDING");
        assertThat(createdEvent.getPayloadJson()).contains("Empresa Corporativa JComercy");

        mockMvc.perform(post("/api/v1/accounts/registration")
                .header("Idempotency-Key", "idem_test_account_duplicate_rfc")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Conflicto con los datos proporcionados."))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PELJ850412ABC"))))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("owner@example.com"))));

        mockMvc.perform(post("/api/v1/accounts/registration")
                .header("Idempotency-Key", "idem_test_account_duplicate_email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest.replace("PELJ850412ABC", "NUEV850412ABC")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Conflicto con los datos proporcionados."));
    }

    @Test
    @DisplayName("Debe fallar con 404 al consultar una cuenta inexistente")
    void shouldReturn404ForNonExistentAccount() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/acc_non_existent")
                .header("Authorization", "Bearer " + token("acc_non_existent")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private static String token(String subject) {
        SecretKey key = Keys.hmacShaKeyFor("local-only-change-me-012345678901234567890123456789".getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder().subject(subject).issuer("jcomercy").issuedAt(now)
            .expiration(new Date(now.getTime() + 300_000)).signWith(key).compact();
    }
}
