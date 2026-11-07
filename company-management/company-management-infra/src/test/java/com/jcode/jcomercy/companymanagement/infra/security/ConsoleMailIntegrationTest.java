package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mientras no hay servidor de correo, en pruebas y en trabajo local los códigos y enlaces salen por la consola
 * ({@code jcomercy.mail.mode=console}). Aquí se comprueba que lo que se imprime sirve de verdad: con el enlace de la
 * consola se cambia una contraseña y se activa un acceso.
 */
@SpringBootTest(properties = {"jcomercy.mail.mode=console", "jcomercy.public-url=https://app.jcomercy.test"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ConsoleMailIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("El enlace de recuperación que sale por la consola cambia la contraseña")
    void theRecoveryLinkPrintedOnTheConsoleChangesThePassword(CapturedOutput output) throws Exception {
        RestWorld.Person person = new RestWorld(mockMvc, objectMapper, jdbc).register();

        mockMvc.perform(post("/api/v1/auth/password-recovery").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + person.email() + "\"}")).andExpect(status().isAccepted());

        String token = awaitCode(output, "recuperar-contrasena", person.email());
        assertThat(output.getOut()).contains("Para: " + person.email()).contains("https://app.jcomercy.test/recuperar-contrasena?codigo=");
        mockMvc.perform(post("/api/v1/auth/password-recovery/confirm").contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\",\"newPassword\":\"NuevaClave456!\"}")).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + person.email() + "\",\"password\":\"NuevaClave456!\"}")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("El enlace de invitación que sale por la consola activa el acceso, y RH ve el correo como enviado")
    void theInvitationLinkPrintedOnTheConsoleActivatesTheAccess(CapturedOutput output) throws Exception {
        RestWorld world = new RestWorld(mockMvc, objectMapper, jdbc);
        RestWorld.Person owner = world.register();
        String account = world.login(owner.email());
        String company = world.createCompany(owner.accountId(), account);
        String token = world.enter(owner.accountId(), company, account);
        String branch = world.createBranch(company, token, "MATRIZ");
        world.activateBranch(company, branch, token);
        world.activateCompany(company, token);

        int n = RestWorld.next();
        String homoclave = Integer.toString(n % 46656, 36).toUpperCase(Locale.ROOT);
        String email = "consola-" + n + "@example.com";
        ObjectNode body = objectMapper.createObjectNode();
        body.put("firstName", "Persona" + n).put("lastName", "Consola Ruiz").put("birthDate", "1990-05-17");
        body.put("rfc", "PRUE850412" + "0".repeat(3 - homoclave.length()) + homoclave);
        body.put("curp", "PRUE850412HDFRRN" + Integer.toString((n / 10) % 36, 36).toUpperCase(Locale.ROOT) + (n % 10));
        body.put("phoneNumber", "5512345678").put("email", email).put("state", "Jalisco").put("address", "Calle 1");
        body.putArray("roleIds").add("rol_sys_staff");
        body.putArray("branchIds").add(branch);
        String created = mockMvc.perform(post("/api/v1/companies/" + company + "/employees").header("Authorization", token)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.invitation.emailStatus").value("SENT"))
            .andReturn().getResponse().getContentAsString();

        String code = awaitCode(output, "activar-acceso", email);
        mockMvc.perform(post("/api/v1/employee-invitations/" + code + "/acceptance").contentType(MediaType.APPLICATION_JSON)
            .content("{\"password\":\"" + RestWorld.PASSWORD + "\"}")).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/companies/" + company + "/employees/" + objectMapper.readTree(created).get("employeeId").asText())
                .header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    /** El código del último enlace de ese tipo impreso para ese correo; el de recuperación sale en otro hilo, así que se espera. */
    private static String awaitCode(CapturedOutput output, String page, String email) throws Exception {
        Pattern pattern = Pattern.compile("Para: " + Pattern.quote(email) + "[\\s\\S]*?" + Pattern.quote(page) + "\\?codigo=(\\S+)");
        long deadline = System.currentTimeMillis() + 5_000;
        Matcher matcher = pattern.matcher(output.getOut());
        while (!matcher.find() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            matcher = pattern.matcher(output.getOut());
        }
        assertThat(output.getOut()).as("el enlace salió por la consola").contains(page + "?codigo=");
        return matcher.group(1);
    }
}
