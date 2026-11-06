package com.jcode.jcomercy.companymanagement.domain.exception;

/** El correo no pudo entregarse al servidor de correo. El mensaje nunca cita al destinatario. */
public class MailDeliveryException extends DomainException {
    public MailDeliveryException(String message) {
        super(message);
    }
}
