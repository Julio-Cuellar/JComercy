package com.jcode.jcomercy.companymanagement.infra.security;

import com.jcode.jcomercy.companymanagement.domain.exception.UnauthorizedAccessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * Cada ruta REST bajo una cuenta o una empresa solo atiende a quien es de esa cuenta o tiene acceso a esa empresa.
 * Va como interceptor y no como un `if` en cada controlador para que una ruta nueva en esos prefijos nazca protegida.
 */
@Component
public class RestOwnershipInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(RestOwnershipInterceptor.class);

    private final JwtAuthorizationService authorization;
    private final EffectivePermissions effectivePermissions;

    public RestOwnershipInterceptor(JwtAuthorizationService authorization, EffectivePermissions effectivePermissions) {
        this.authorization = authorization;
        this.effectivePermissions = effectivePermissions;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Sin variables de ruta no hay ningún controlador de cuentas ni de empresas que proteger (por ejemplo, una ruta inexistente).
        Map<String, String> variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables == null) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String accountId = variables.get("publicAccountId");
        if (accountId != null && !authorization.canAccessAccount(authentication, accountId)) {
            deny(authentication, request);
            throw new UnauthorizedAccessException("No tienes acceso a esta cuenta. Inicia sesión con la cuenta que corresponde.");
        }
        String companyId = variables.get("publicCompanyId");
        if (companyId != null && !canWorkWithCompany(authentication, companyId)) {
            deny(authentication, request);
            throw new UnauthorizedAccessException("No tienes acceso a esta empresa. Pide acceso a quien administra la cuenta.");
        }
        return true;
    }

    /**
     * Entra a la empresa quien es dueña de su cuenta o tiene en ella una membresía activa, según la base de datos. El
     * token no nombra empresas (ERR-011), y así una membresía suspendida o una empresa recién creada se reflejan en
     * la siguiente petición. Una empresa que no existe no da acceso a nadie.
     */
    private boolean canWorkWithCompany(Authentication authentication, String companyId) {
        String subject = authentication == null ? null : authentication.getName();
        return effectivePermissions.belongsToCompany(subject, companyId);
    }

    private static void deny(Authentication authentication, HttpServletRequest request) {
        log.warn("Acceso denegado actor={} metodo={} ruta={}",
            authentication == null ? "anonimo" : authentication.getName(), request.getMethod(), request.getRequestURI());
    }
}
