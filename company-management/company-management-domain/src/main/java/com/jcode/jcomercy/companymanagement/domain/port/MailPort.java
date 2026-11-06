package com.jcode.jcomercy.companymanagement.domain.port;

/**
 * Por donde el módulo manda un correo: la recuperación de contraseña y, después, las invitaciones. Quién lo entrega
 * (un servidor SMTP, un adaptador de pruebas) lo decide la configuración, no el código que lo pide.
 */
public interface MailPort {

    /** Un correo de texto: a quién va, el asunto y el cuerpo. */
    record Message(String to, String subject, String body) { }

    /** @throws com.jcode.jcomercy.companymanagement.domain.exception.MailDeliveryException si no se pudo entregar al servidor */
    void send(Message message);
}
