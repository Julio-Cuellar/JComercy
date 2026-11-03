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
 * Cada ruta REST bajo una cuenta o una empresa solo atiende a quien es de esa cuenta o tiene acceso a esa empresa, y con
 * la sesión que le corresponde: las rutas de la cuenta, con el token de la cuenta; las de una empresa, con el token que
 * se entrega al entrar a esa empresa (ERR-011). Así un token de empresa robado no administra la cuenta ni lista las
 * demás empresas. Va como interceptor y no como un `if` en cada controlador para que una ruta nueva en esos prefijos
 * nazca protegida.
 */
@Component
public class RestOwnershipInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(RestOwnershipInterceptor.class);

    public static final String ACCOUNT_DENIED = "No tienes acceso a esta cuenta. Inicia sesión con la cuenta que corresponde.";
    public static final String COMPANY_DENIED = "No tienes acceso a esta empresa. Pide acceso a quien administra la cuenta.";
    public static final String COMPANY_NOT_CHOSEN =
        "Elige primero la empresa con la que vas a trabajar. Entra a una desde «Tus empresas» e intenta de nuevo.";
    public static final String OTHER_COMPANY_SESSION =
        "Tu sesión es de otra empresa. Entra a esta empresa desde «Tus empresas» e intenta de nuevo.";
    public static final String ACCOUNT_ONLY =
        "Esto se hace desde tu cuenta, no desde una empresa. Vuelve a tu cuenta e intenta de nuevo.";

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
            throw new UnauthorizedAccessException(ACCOUNT_DENIED);
        }
        // La cuenta se administra con la sesión de la cuenta, no con la de una empresa.
        if (accountId != null && !authorization.isAccountSession(authentication)) {
            deny(authentication, request);
            throw new UnauthorizedAccessException(ACCOUNT_ONLY);
        }
        String companyId = variables.get("publicCompanyId");
        if (companyId != null && !authorization.isSessionOfCompany(authentication, companyId)) {
            // El tipo de token se revisa antes de buscar la empresa: la respuesta no dice cuáles existen.
            deny(authentication, request);
            throw new UnauthorizedAccessException(authorization.isAccountSession(authentication)
                ? COMPANY_NOT_CHOSEN : OTHER_COMPANY_SESSION);
        }
        if (companyId != null && !canWorkWithCompany(authentication, companyId)) {
            deny(authentication, request);
            throw new UnauthorizedAccessException(COMPANY_DENIED);
        }
        return true;
    }

    /**
     * Entra a la empresa quien es dueña de su cuenta o tiene en ella una membresía activa, según la base de datos. El
     * token de empresa dice a cuál entró la persona, no si sigue perteneciendo a ella (ERR-011), y así una membresía
     * suspendida se refleja en la siguiente petición. Una empresa que no existe no da acceso a nadie.
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
