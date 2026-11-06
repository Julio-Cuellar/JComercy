package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reenviar la invitación está controlado para no llenar de correos a una persona: hay un tiempo mínimo entre envíos. */
@SpringBootTest(properties = "jcomercy.invitations.resend-min-interval-seconds=3600")
@AutoConfigureMockMvc
class EmployeeInvitationResendLimitIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("Reenviar enseguida da conflicto con el tiempo que hay que esperar")
    void resendingTooSoonIsAConflict() throws Exception {
        RestWorld world = new RestWorld(mockMvc, objectMapper, jdbc);
        RestWorld.Person owner = world.register();
        String account = world.login(owner.email());
        String company = world.createCompany(owner.accountId(), account);
        String token = world.enter(owner.accountId(), company, account);
        String branch = world.createBranch(company, token, "MATRIZ");

        int n = RestWorld.next();
        String homoclave = Integer.toString(n % 46656, 36).toUpperCase(Locale.ROOT);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("firstName", "Empleada" + n).put("lastName", "Prueba Ruiz").put("birthDate", "1990-05-17");
        body.put("rfc", "PRUE850412" + "0".repeat(3 - homoclave.length()) + homoclave);
        body.put("curp", "PRUE850412HDFRRN" + Integer.toString((n / 10) % 36, 36).toUpperCase(Locale.ROOT) + (n % 10));
        body.put("phoneNumber", "5512345678").put("email", "limite-" + n + "@example.com").put("state", "Jalisco").put("address", "Calle 1");
        body.putArray("roleIds").add("rol_sys_staff");
        body.putArray("branchIds").add(branch);

        String created = mockMvc.perform(post("/api/v1/companies/" + company + "/employees").header("Authorization", token)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).get("employeeId").asText();

        mockMvc.perform(post("/api/v1/companies/" + company + "/employees/" + id + "/invitation").header("Authorization", token)
                .header("Idempotency-Key", UUID.randomUUID().toString()))
            .andExpect(status().isConflict());
    }
}
