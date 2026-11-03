package com.jcode.jcomercy.companymanagement.infra.security;

import com.jcode.jcomercy.companymanagement.domain.exception.UnauthorizedAccessException;
import com.jcode.jcomercy.companymanagement.domain.port.CompanyRepositoryPort;
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
    private final CompanyRepositoryPort companies;

    public RestOwnershipInterceptor(JwtAuthorizationService authorization, CompanyRepositoryPort companies) {
        this.authorization = authorization;
        this.companies = companies;
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
     * El claim del token da acceso a las empresas que ya existían al iniciar sesión. Las creadas después no están en
     * el claim, así que la cuenta dueña de la empresa también entra. Una empresa que no existe no da acceso a nadie.
     */
    private boolean canWorkWithCompany(Authentication authentication, String companyId) {
        if (authorization.canAccessCompany(authentication, companyId)) {
            return true;
        }
        String subject = authentication == null ? null : authentication.getName();
        return subject != null && companies.findByPublicCompanyId(companyId)
            .map(company -> subject.equals(company.getPublicAccountId()))
            .orElse(false);
    }

    private static void deny(Authentication authentication, HttpServletRequest request) {
        log.warn("Acceso denegado actor={} metodo={} ruta={}",
            authentication == null ? "anonimo" : authentication.getName(), request.getMethod(), request.getRequestURI());
    }
}
