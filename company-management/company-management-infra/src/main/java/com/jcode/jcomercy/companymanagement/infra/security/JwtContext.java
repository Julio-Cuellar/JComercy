package com.jcode.jcomercy.companymanagement.infra.security;

import org.springframework.security.core.Authentication;

/**
 * Qué clase de sesión trae la petición: la de la cuenta (el inicio de sesión) o la de una empresa (el claim `ctx`).
 * Company-management no lee del token las empresas ni los permisos: los calcula de la base de datos en cada petición.
 */
public record JwtContext(String accountId, String companyContext) {
    public static JwtContext from(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof JwtContext context)) {
            return new JwtContext(null, null);
        }
        return context;
    }

    /** El token del inicio de sesión: dice quién eres y no está atado a ninguna empresa. */
    public boolean isAccountToken() {
        return accountId != null && companyContext == null;
    }

    /** El token que se entrega al entrar a una empresa: vale solo para esa empresa. */
    public boolean isTokenOfCompany(String companyId) {
        return accountId != null && companyContext != null && companyContext.equals(companyId);
    }
}
