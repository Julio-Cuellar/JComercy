package com.jcode.jcomercy.companymanagement.infra.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthorizationService {
    public boolean canAccessAccount(Authentication authentication, String accountId) {
        return authentication != null && authentication.isAuthenticated()
                && accountId != null && accountId.equals(authentication.getName());
    }

    /** Si la petición trae el token del inicio de sesión, el de la cuenta. */
    public boolean isAccountSession(Authentication authentication) {
        return JwtContext.from(authentication).isAccountToken();
    }

    /** Si la petición trae el token que se entregó al entrar a esa empresa. */
    public boolean isSessionOfCompany(Authentication authentication, String companyId) {
        return JwtContext.from(authentication).isTokenOfCompany(companyId);
    }
}
