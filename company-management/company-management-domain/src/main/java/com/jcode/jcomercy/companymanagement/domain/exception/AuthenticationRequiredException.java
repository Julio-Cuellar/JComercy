package com.jcode.jcomercy.companymanagement.domain.exception;

/** Para hacer esto hace falta haber iniciado sesión con una cuenta. Responde 401. */
public class AuthenticationRequiredException extends DomainException {
    public AuthenticationRequiredException(String message) {
        super(message);
    }
}
