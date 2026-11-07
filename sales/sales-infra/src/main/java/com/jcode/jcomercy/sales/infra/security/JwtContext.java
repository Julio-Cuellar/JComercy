package com.jcode.jcomercy.sales.infra.security;

import org.springframework.security.core.Authentication;

import java.util.Collection;
import java.util.List;

/** Lo que el token de empresa dice de quien llama: quién es, y las empresas y sucursales que puede tocar. */
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

    public boolean canAccessBranch(String branchId) {
        return branchId != null && branchIds.contains(branchId);
    }

    public static List<String> claimValues(Object claim) {
        if (!(claim instanceof Collection<?> values)) return List.of();
        return values.stream().filter(String.class::isInstance).map(String.class::cast)
                .filter(value -> !value.isBlank()).distinct().toList();
    }
}
