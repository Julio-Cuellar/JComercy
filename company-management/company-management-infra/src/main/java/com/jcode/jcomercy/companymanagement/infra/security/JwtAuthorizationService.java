package com.jcode.jcomercy.companymanagement.infra.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthorizationService {
    public boolean canAccessCompany(Authentication authentication, String companyId) {
        return JwtContext.from(authentication).canAccessCompany(companyId);
    }

    public boolean canAccessAccount(Authentication authentication, String accountId) {
        return authentication != null && authentication.isAuthenticated()
                && accountId != null && accountId.equals(authentication.getName());
    }
}
