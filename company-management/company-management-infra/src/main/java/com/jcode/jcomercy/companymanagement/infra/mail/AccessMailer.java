package com.jcode.jcomercy.companymanagement.infra.mail;

import com.jcode.jcomercy.companymanagement.domain.port.MailPort;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Los correos de acceso: lo que le llega a una persona para recuperar su contraseña. El enlace apunta a la pantalla de la
 * aplicación ({@code jcomercy.public-url}) y lleva el código de un solo uso.
 *
 * <p>El correo sale cuando la transacción que guardó el código ya se confirmó, para que el enlace funcione al abrirlo, y en
 * otro hilo: si saliera en el mismo, pedir la recuperación de una cuenta que existe tardaría más que la de una que no, y
 * eso le diría a cualquiera quién tiene cuenta. Un fallo se anota en el registro sin el código ni el destinatario.
 */
@Component
public class AccessMailer {
    private static final Logger log = LoggerFactory.getLogger(AccessMailer.class);
    static final String RECOVERY_SUBJECT = "Cambia tu contraseña de JComercy";

    private final MailPort mail;
    private final String publicUrl;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "access-mailer");
        thread.setDaemon(true);
        return thread;
    });

    public AccessMailer(MailPort mail, @Value("${jcomercy.public-url:http://localhost:3000}") String publicUrl) {
        this.mail = mail;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    /** Manda el enlace para elegir una contraseña nueva, después de confirmar la transacción en curso. */
    public void sendPasswordRecovery(String email, String token) {
        MailPort.Message message = new MailPort.Message(email, RECOVERY_SUBJECT, """
            Hola:

            Pediste cambiar tu contraseña de JComercy. Abre este enlace para elegir una nueva:

            %s/recuperar-contrasena?codigo=%s

            El enlace vale 15 minutos y se puede usar una sola vez. Si no lo pediste, no hagas nada: tu contraseña sigue igual.

            JComercy
            """.formatted(publicUrl, token));
        afterCommit(() -> executor.execute(() -> {
            try {
                mail.send(message);
            } catch (RuntimeException e) {
                log.error("No se pudo enviar el correo de recuperación de contraseña causa={}", e.getClass().getSimpleName());
            }
        }));
    }

    private static void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
