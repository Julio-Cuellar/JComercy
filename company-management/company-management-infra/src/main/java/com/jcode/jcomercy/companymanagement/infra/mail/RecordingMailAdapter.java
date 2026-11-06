package com.jcode.jcomercy.companymanagement.infra.mail;

import com.jcode.jcomercy.companymanagement.domain.port.MailPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Guarda los correos en memoria en vez de mandarlos: para las pruebas y para trabajar en local sin servidor de correo
 * ({@code jcomercy.mail.mode=recording}). Nunca debe quedar activo en producción: ahí nadie recibiría nada.
 */
@Component
@ConditionalOnProperty(name = "jcomercy.mail.mode", havingValue = "recording")
public class RecordingMailAdapter implements MailPort {

    private final List<Message> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(Message message) {
        sent.add(message);
    }

    /** Lo que se mandó a ese correo, en el orden en que salió. */
    public List<Message> sentTo(String email) {
        return sent.stream().filter(message -> message.to().equalsIgnoreCase(email)).toList();
    }

    public void clear() {
        sent.clear();
    }
}
