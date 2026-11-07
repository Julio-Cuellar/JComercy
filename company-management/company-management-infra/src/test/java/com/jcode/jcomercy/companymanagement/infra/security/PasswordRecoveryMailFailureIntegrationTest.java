package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Si el servidor de correo no responde, la recuperación de contraseña responde igual que siempre: un error distinto
 * para las cuentas que existen le diría a cualquiera quién tiene cuenta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PasswordRecoveryMailFailureIntegrationTest.FailingMail.class)
class PasswordRecoveryMailFailureIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("Si el correo no puede salir, la respuesta sigue siendo la misma y no se revela quién tiene cuenta")
    void aFailedSendDoesNotChangeTheAnswer() throws Exception {
        RestWorld.Person person = new RestWorld(mockMvc, objectMapper, null).register();

        mockMvc.perform(post("/api/v1/auth/password-recovery").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + person.email() + "\"}"))
            .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/auth/password-recovery").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nadie-" + RestWorld.next() + "@example.com\"}"))
            .andExpect(status().isAccepted());
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
