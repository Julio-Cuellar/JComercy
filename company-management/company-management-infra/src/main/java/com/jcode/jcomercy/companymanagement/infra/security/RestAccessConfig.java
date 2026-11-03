package com.jcode.jcomercy.companymanagement.infra.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Control de acceso de las rutas REST de cuentas y empresas, en dos pasos y en este orden: primero a quién pertenece
 * lo que se pide ({@link RestOwnershipInterceptor}) y luego qué permite el rol de quien lo pide
 * ({@link RestPermissionInterceptor}).
 */
@Configuration
public class RestAccessConfig implements WebMvcConfigurer {

    private static final String[] PROTECTED_PATHS = {
        "/api/v1/accounts/{publicAccountId}/**", "/api/v1/companies/{publicCompanyId}/**"
    };
    // El registro es público: todavía no existe la cuenta a la que pertenecer.
    private static final String PUBLIC_PATH = "/api/v1/accounts/registration";

    private final RestOwnershipInterceptor ownership;
    private final RestPermissionInterceptor permissions;

    public RestAccessConfig(RestOwnershipInterceptor ownership, RestPermissionInterceptor permissions) {
        this.ownership = ownership;
        this.permissions = permissions;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ownership).addPathPatterns(PROTECTED_PATHS).excludePathPatterns(PUBLIC_PATH);
        registry.addInterceptor(permissions).addPathPatterns(PROTECTED_PATHS).excludePathPatterns(PUBLIC_PATH);
    }
}
