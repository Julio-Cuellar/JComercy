package com.jcode.jcomercy.sales.infra.security;

import com.jcode.jcomercy.core.authorization.Permissions;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Ventas decide con el token de empresa y nada más: sus empresas, sus sucursales y sus permisos. Que la persona siga
 * perteneciendo a la empresa lo comprueba company-management al emitir el token (ver el modelo de autorización).
 */
@Component
public class JwtAuthorizationService {
    private static final String PERMISSION_PREFIX = "PERM_";

    public boolean canAccessCompany(Authentication authentication, String companyId) {
        return JwtContext.from(authentication).canAccessCompany(companyId);
    }

    public boolean canAccessBranch(Authentication authentication, String branchId) {
        return JwtContext.from(authentication).canAccessBranch(branchId);
    }

    public boolean hasPermission(Authentication authentication, String permission) {
        if (authentication == null || permission == null) return false;
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith(PERMISSION_PREFIX))
                .map(authority -> authority.substring(PERMISSION_PREFIX.length()))
                .anyMatch(granted -> Permissions.matches(granted, permission));
    }
}
