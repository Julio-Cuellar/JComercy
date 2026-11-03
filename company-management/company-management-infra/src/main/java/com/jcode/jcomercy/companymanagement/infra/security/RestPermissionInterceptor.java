package com.jcode.jcomercy.companymanagement.infra.security;

import com.jcode.jcomercy.companymanagement.domain.exception.UnauthorizedAccessException;
import com.jcode.jcomercy.core.authorization.Permissions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Set;

/**
 * Cada ruta REST bajo una cuenta o una empresa exige el permiso que declara con {@link RequiresPermission}. Corre
 * después de {@link RestOwnershipInterceptor}: primero se comprueba que la persona pertenezca a la cuenta o empresa, y
 * luego que su rol permita hacer esto en ella. Una ruta sin anotación se rechaza: lo que no declara su permiso no se
 * deja abierto por olvido.
 */
@Component
public class RestPermissionInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(RestPermissionInterceptor.class);
    private static final String ASK_THE_ADMIN = "Pide acceso a quien administra la cuenta.";

    private final EffectivePermissions effectivePermissions;

    public RestPermissionInterceptor(EffectivePermissions effectivePermissions) {
        this.effectivePermissions = effectivePermissions;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Lo que no es un método de controlador (una ruta inexistente, por ejemplo) no tiene permiso que exigir.
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = authentication == null ? null : authentication.getName();
        RequiresPermission required = method.getMethodAnnotation(RequiresPermission.class);
        if (required == null) {
            log.error("Ruta sin @RequiresPermission, se rechaza metodo={} ruta={}", request.getMethod(), request.getRequestURI());
            deny(actor, request, "(sin declarar)");
            throw new UnauthorizedAccessException("No tienes permiso para hacer esto. " + ASK_THE_ADMIN);
        }
        Map<String, String> variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        Set<String> granted = permissionsFor(actor, variables);
        if (!Permissions.grants(granted, required.value())) {
            deny(actor, request, required.value());
            throw new UnauthorizedAccessException("Tu rol no permite " + required.action() + ". " + ASK_THE_ADMIN);
        }
        return true;
    }

    private Set<String> permissionsFor(String actor, Map<String, String> variables) {
        String companyId = variables == null ? null : variables.get("publicCompanyId");
        if (companyId != null) {
            return effectivePermissions.inCompany(actor, companyId);
        }
        return effectivePermissions.inAccount(actor, variables == null ? null : variables.get("publicAccountId"));
    }

    private static void deny(String actor, HttpServletRequest request, String permission) {
        log.warn("Permiso denegado actor={} permiso={} metodo={} ruta={}",
            actor == null ? "anonimo" : actor, permission, request.getMethod(), request.getRequestURI());
    }
}
