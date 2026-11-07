package com.jcode.jcomercy.companymanagement.infra.mail;

import com.jcode.jcomercy.companymanagement.domain.exception.MailDeliveryException;
import com.jcode.jcomercy.companymanagement.domain.port.MailPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * El modo por defecto: no hay servidor de correo configurado. Falla a la vista en vez de tirar el correo, para que quien
 * administra el sistema lo note en el registro y no descubra semanas después que nadie recibía nada.
 */
@Component
@ConditionalOnProperty(name = "jcomercy.mail.mode", havingValue = "disabled", matchIfMissing = true)
public class DisabledMailAdapter implements MailPort {

    @Override
    public void send(Message message) {
        throw new MailDeliveryException("El envío de correo no está configurado. Indica jcomercy.mail.mode=smtp y los datos del servidor de correo.");
    }
}
