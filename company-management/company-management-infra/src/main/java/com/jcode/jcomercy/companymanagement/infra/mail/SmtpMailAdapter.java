package com.jcode.jcomercy.companymanagement.infra.mail;

import com.jcode.jcomercy.companymanagement.domain.exception.MailDeliveryException;
import com.jcode.jcomercy.companymanagement.domain.port.MailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Entrega el correo a un servidor SMTP ({@code jcomercy.mail.mode=smtp}). El servidor se configura con
 * {@code spring.mail.host}, {@code spring.mail.port}, {@code spring.mail.username} y {@code spring.mail.password}; si
 * falta, el sistema no arranca en vez de fallar al primer correo.
 */
@Component
@ConditionalOnProperty(name = "jcomercy.mail.mode", havingValue = "smtp")
public class SmtpMailAdapter implements MailPort {
    private static final Logger log = LoggerFactory.getLogger(SmtpMailAdapter.class);

    private final JavaMailSender sender;
    private final String from;

    public SmtpMailAdapter(JavaMailSender sender, @Value("${jcomercy.mail.from:no-responder@jcomercy.com}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(Message message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        try {
            sender.send(mail);
        } catch (MailException e) {
            // Ni el mensaje de la excepción ni el destinatario van al registro o al error: pueden citar el correo de una persona.
            log.warn("El servidor de correo no aceptó el mensaje causa={}", e.getClass().getSimpleName());
            throw new MailDeliveryException("El servidor de correo no aceptó el mensaje.");
        }
    }
}
