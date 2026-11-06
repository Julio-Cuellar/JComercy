package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jcode.jcomercy.companymanagement.domain.exception.MailDeliveryException;
import com.jcode.jcomercy.companymanagement.domain.port.MailPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Si el servidor de correo no responde, el alta se guarda igual y RH ve que el correo no salió, para reenviarlo. Es lo
 * contrario de la recuperación de contraseña: ahí no se avisa nada, porque quien pide es desconocido; aquí quien
 * registra es una persona de la empresa con permiso, y necesita saberlo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EmployeeInvitationMailFailureIntegrationTest.FailingMail.class)
class EmployeeInvitationMailFailureIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("Si el correo no sale, el alta queda guardada y RH ve «correo no enviado»")
    void aFailedSendLeavesTheRegistrationAndShowsItToHr() throws Exception {
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
        body.put("phoneNumber", "5512345678").put("email", "falla-" + n + "@example.com").put("state", "Jalisco").put("address", "Calle 1");
        body.putArray("roleIds").add("rol_sys_staff");
        body.putArray("branchIds").add(branch);

        String created = mockMvc.perform(post("/api/v1/companies/" + company + "/employees").header("Authorization", token)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.invitation.status").value("PENDING"))
            .andExpect(jsonPath("$.invitation.emailStatus").value("FAILED"))
            .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(created).get("employeeId").asText();
        mockMvc.perform(get("/api/v1/companies/" + company + "/employees/" + id).header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.invitation.emailStatus").value("FAILED"));
    }

    @TestConfiguration
    static class FailingMail {
        @Bean
        @Primary
        MailPort failingMail() {
            return message -> {
                throw new MailDeliveryException("El servidor de correo no responde.");
            };
        }
    }
}
