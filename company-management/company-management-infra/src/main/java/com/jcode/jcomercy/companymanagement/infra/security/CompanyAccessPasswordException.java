package com.jcode.jcomercy.companymanagement.infra.security;

/**
 * La contraseña de un acceso a una empresa falta, es incorrecta o el acceso está bloqueado por demasiados intentos.
 * Cada caso tiene su propio código para que quien llama sepa qué hacer, sin parecerse a la falta de permisos.
 */
public class CompanyAccessPasswordException extends RuntimeException {

    public enum Kind {
        /** Falta la contraseña de la empresa que se pide para entrar o para cambiarla. */
        COMPANY_PASSWORD_REQUIRED(403),
        /** La contraseña de la empresa no es correcta. */
        COMPANY_PASSWORD_INVALID(403),
        /** Falta la contraseña de la cuenta que se pide para quitar la de la empresa. */
        ACCOUNT_PASSWORD_REQUIRED(403),
        /** La contraseña de la cuenta no es correcta. */
        ACCOUNT_PASSWORD_INVALID(403),
        /** Demasiados intentos fallidos: el acceso espera antes de volver a probar. */
        COMPANY_ACCESS_LOCKED(429);

        private final int status;

        Kind(int status) {
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    private final Kind kind;
    private final long retryAfterSeconds;

    public CompanyAccessPasswordException(Kind kind, String message) {
        this(kind, message, 0);
    }

    public CompanyAccessPasswordException(Kind kind, String message, long retryAfterSeconds) {
        super(message);
        this.kind = kind;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Kind kind() {
        return kind;
    }

    /** Segundos que faltan para poder volver a intentar; solo tiene sentido cuando el acceso está bloqueado. */
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
