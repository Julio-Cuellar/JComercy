package com.jcode.jcomercy.companymanagement.domain.exception;

/** La operación choca con el estado actual de algo: una versión vieja, un rol que alguien todavía usa. Responde 409. */
public class ConflictException extends DomainException {
    public ConflictException(String message) {
        super(message);
    }
}
