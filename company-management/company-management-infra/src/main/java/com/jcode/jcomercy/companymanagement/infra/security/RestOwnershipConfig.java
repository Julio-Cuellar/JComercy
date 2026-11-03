package com.jcode.jcomercy.companymanagement.infra.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RestOwnershipConfig implements WebMvcConfigurer {

    private final RestOwnershipInterceptor interceptor;

    public RestOwnershipConfig(RestOwnershipInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
            .addPathPatterns("/api/v1/accounts/{publicAccountId}/**", "/api/v1/companies/{publicCompanyId}/**")
            // El registro es público: todavía no existe la cuenta a la que pertenecer.
            .excludePathPatterns("/api/v1/accounts/registration");
    }
}
