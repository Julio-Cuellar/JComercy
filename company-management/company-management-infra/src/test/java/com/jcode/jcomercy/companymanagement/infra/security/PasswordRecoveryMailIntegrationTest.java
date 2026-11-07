package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.companymanagement.domain.port.MailPort;
import com.jcode.jcomercy.companymanagement.infra.mail.RecordingMailAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ERR-029: la recuperación de contraseña generaba su código y no lo entregaba por ningún medio, así que nadie podía
 * recuperar su acceso. Ahora el código sale en un correo con un enlace de un solo uso, y la respuesta es la misma exista
 * o no el correo en el sistema.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordRecoveryMailIntegrationTest {

    private static final String LINK = "https://app.jcomercy.test/recuperar-contrasena?codigo=";
    private static final String NEW_PASSWORD = "NuevaClave456!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RecordingMailAdapter mail;

    @Test
    @DisplayName("El correo trae un enlace de un solo uso que sirve para elegir una contraseña nueva")
    void theEmailCarriesAOneTimeLinkThatChangesThePassword() throws Exception {
        RestWorld.Person person = new RestWorld(mockMvc, objectMapper, null).register();

        requestRecovery(person.email()).andExpect(status().isAccepted());

        MailPort.Message message = awaitMessages(person.email(), 1).get(0);
        assertThat(message.subject()).isEqualTo("Cambia tu contraseña de JComercy");
        assertThat(message.body()).contains(LINK).contains("15 minutos").contains("una sola vez");
        assertThat(message.body()).as("el correo no lleva datos que no hacen falta")
            .doesNotContain(person.accountId()).doesNotContain(RestWorld.PASSWORD);

        String token = tokenOf(message);
        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());
        login(person.email(), NEW_PASSWORD).andExpect(status().isOk());
        login(person.email(), RestWorld.PASSWORD).andExpect(status().isUnauthorized());
        confirm(token, "OtraClave789!").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Cada petición manda un enlace distinto, y el correo en mayúsculas encuentra la misma cuenta")
    void eachRequestSendsADifferentLinkAndTheEmailIsCaseInsensitive() throws Exception {
        RestWorld.Person person = new RestWorld(mockMvc, objectMapper, null).register();

        requestRecovery(person.email()).andExpect(status().isAccepted());
        requestRecovery(person.email().toUpperCase()).andExpect(status().isAccepted());

        List<MailPort.Message> messages = awaitMessages(person.email(), 2);
        assertThat(messages).hasSize(2);
        assertThat(tokenOf(messages.get(0))).isNotEqualTo(tokenOf(messages.get(1)));
    }

    @Test
    @DisplayName("Un correo que no existe recibe la misma respuesta y no se le manda nada")
    void anUnknownEmailGetsTheSameAnswerAndNothingIsSent() throws Exception {
        String unknown = "nadie-" + RestWorld.next() + "@example.com";

        requestRecovery(unknown).andExpect(status().isAccepted());

        Thread.sleep(500);
        assertThat(mail.sentTo(unknown)).isEmpty();
    }

    private ResultActions requestRecovery(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-recovery").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions confirm(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-recovery/confirm").contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    /** El correo sale en otro hilo, para que la respuesta no tarde distinto según la cuenta exista o no. */
    private List<MailPort.Message> awaitMessages(String email, int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (mail.sentTo(email).size() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        return mail.sentTo(email);
    }

    private static String tokenOf(MailPort.Message message) {
        Matcher matcher = Pattern.compile(Pattern.quote(LINK) + "(\\S+)").matcher(message.body());
        assertThat(matcher.find()).as("el correo trae el enlace con el código").isTrue();
        return matcher.group(1);
    }
}
