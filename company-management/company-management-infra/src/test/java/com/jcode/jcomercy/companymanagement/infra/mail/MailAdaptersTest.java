package com.jcode.jcomercy.companymanagement.infra.mail;

import com.jcode.jcomercy.companymanagement.domain.exception.MailDeliveryException;
import com.jcode.jcomercy.companymanagement.domain.port.MailPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Los adaptadores del puerto de correo y cuál se elige según {@code jcomercy.mail.mode}. */
@ExtendWith(OutputCaptureExtension.class)
class MailAdaptersTest {

    private static final MailPort.Message MESSAGE = new MailPort.Message("ana@example.com", "Asunto", "Cuerpo del correo");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(DisabledMailAdapter.class, RecordingMailAdapter.class, SmtpMailAdapter.class, ConsoleMailAdapter.class);

    @Test
    @DisplayName("El modo consola imprime a quién va el correo, su asunto y su cuerpo, enlace incluido, y no manda nada a nadie")
    void consolePrintsTheWholeMessage(CapturedOutput output) {
        MailPort.Message withLink = new MailPort.Message("ana@example.com", "Cambia tu contraseña",
            "Abre este enlace:\n\nhttp://localhost:3000/recuperar-contrasena?codigo=abc123\n\nVale 15 minutos.");

        new ConsoleMailAdapter(new MockEnvironment()).send(withLink);

        assertThat(output.getOut()).contains("Para: ana@example.com").contains("Asunto: Cambia tu contraseña")
            .contains("http://localhost:3000/recuperar-contrasena?codigo=abc123").contains("Vale 15 minutos.")
            .contains("no se mandó a nadie");
    }

    @Test
    @DisplayName("El modo consola elige su adaptador")
    void consoleMode() {
        contextRunner.withPropertyValues("jcomercy.mail.mode=console").run(context -> {
            assertThat(context).hasSingleBean(MailPort.class);
            assertThat(context.getBean(MailPort.class)).isInstanceOf(ConsoleMailAdapter.class);
        });
    }

    @Test
    @DisplayName("El modo consola impide arrancar en producción: ahí los códigos de acceso acabarían en el registro")
    void consoleModeRefusesToStartInProduction() {
        contextRunner.withPropertyValues("jcomercy.mail.mode=console", "spring.profiles.active=prod").run(context ->
            assertThat(context).hasFailed());
        contextRunner.withPropertyValues("jcomercy.mail.mode=console", "spring.profiles.active=production").run(context ->
            assertThat(context).hasFailed());
        contextRunner.withPropertyValues("jcomercy.mail.mode=console", "spring.profiles.active=local").run(context ->
            assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("El envío por SMTP arma el correo con remitente, destinatario, asunto y cuerpo")
    void smtpBuildsTheMessage() {
        JavaMailSender sender = mock(JavaMailSender.class);

        new SmtpMailAdapter(sender, "no-responder@jcomercy.test").send(MESSAGE);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().getFrom()).isEqualTo("no-responder@jcomercy.test");
        assertThat(sent.getValue().getTo()).containsExactly("ana@example.com");
        assertThat(sent.getValue().getSubject()).isEqualTo("Asunto");
        assertThat(sent.getValue().getText()).isEqualTo("Cuerpo del correo");
    }

    @Test
    @DisplayName("Si el servidor SMTP falla, el error no cita el destinatario")
    void smtpFailureIsReportedWithoutTheRecipient() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("falló el envío a ana@example.com")).when(sender).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));

        assertThatThrownBy(() -> new SmtpMailAdapter(sender, "no-responder@jcomercy.test").send(MESSAGE))
            .isInstanceOf(MailDeliveryException.class)
            .hasMessageNotContaining("ana@example.com");
    }

    @Test
    @DisplayName("Sin configurar, el envío falla a la vista en vez de perder el correo en silencio")
    void disabledFailsLoudly() {
        assertThatThrownBy(() -> new DisabledMailAdapter().send(MESSAGE))
            .isInstanceOf(MailDeliveryException.class)
            .hasMessageContaining("no está configurado");
    }

    @Test
    @DisplayName("El adaptador de pruebas guarda lo que se mandó y se puede vaciar")
    void recordingKeepsWhatWasSent() {
        RecordingMailAdapter recording = new RecordingMailAdapter();

        recording.send(MESSAGE);

        assertThat(recording.sentTo("ana@example.com")).containsExactly(MESSAGE);
        assertThat(recording.sentTo("otra@example.com")).isEmpty();
        recording.clear();
        assertThat(recording.sentTo("ana@example.com")).isEmpty();
    }

    @Test
    @DisplayName("Sin indicar modo se elige el adaptador que falla a la vista")
    void defaultModeIsDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MailPort.class);
            assertThat(context.getBean(MailPort.class)).isInstanceOf(DisabledMailAdapter.class);
        });
    }

    @Test
    @DisplayName("El modo recording elige el adaptador de pruebas")
    void recordingMode() {
        contextRunner.withPropertyValues("jcomercy.mail.mode=recording").run(context -> {
            assertThat(context).hasSingleBean(MailPort.class);
            assertThat(context.getBean(MailPort.class)).isInstanceOf(RecordingMailAdapter.class);
        });
    }

    @Test
    @DisplayName("El modo smtp elige el adaptador SMTP con su remitente configurado")
    void smtpMode() {
        contextRunner.withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
            .withPropertyValues("jcomercy.mail.mode=smtp", "jcomercy.mail.from=ventas@jcomercy.test").run(context -> {
                assertThat(context).hasSingleBean(MailPort.class);
                assertThat(context.getBean(MailPort.class)).isInstanceOf(SmtpMailAdapter.class);
            });
    }

    @Test
    @DisplayName("El modo smtp sin servidor configurado impide arrancar, en vez de fallar al primer correo")
    void smtpWithoutAServerFailsToStart() {
        contextRunner.withPropertyValues("jcomercy.mail.mode=smtp").run(context ->
            assertThat(context).hasFailed());
    }
}
