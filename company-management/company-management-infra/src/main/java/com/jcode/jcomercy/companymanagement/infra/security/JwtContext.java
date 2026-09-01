package com.jcode.jcomercy.companymanagement.infra.security;

import org.springframework.security.core.Authentication;

import java.util.Collection;
import java.util.List;

public record JwtContext(String accountId, List<String> companyIds, List<String> branchIds) {
    public static JwtContext from(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof JwtContext context)) {
            return new JwtContext(null, List.of(), List.of());
        }
        return context;
    }

    public boolean canAccessCompany(String companyId) {
        return companyId != null && companyIds.contains(companyId);
    }

    public static List<String> claimValues(Object claim) {
        if (!(claim instanceof Collection<?> values)) return List.of();
        return values.stream().filter(String.class::isInstance).map(String.class::cast)
                .filter(value -> !value.isBlank()).distinct().toList();
    }
}
