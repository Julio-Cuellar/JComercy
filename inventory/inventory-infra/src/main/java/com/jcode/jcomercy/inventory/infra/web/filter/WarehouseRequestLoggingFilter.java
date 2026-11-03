package com.jcode.jcomercy.inventory.infra.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Registra la entrada y salida de cada petición REST del módulo de almacenes sin exponer tokens ni cuerpos. */
@Component
public class WarehouseRequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(WarehouseRequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.currentTimeMillis();
        log.info("Warehouse request start method={} uri={} correlationId={}", request.getMethod(),
                request.getRequestURI(), request.getHeader("X-Correlation-Id"));
        try {
            chain.doFilter(request, response);
            log.info("Warehouse request end method={} uri={} status={} durationMs={}", request.getMethod(),
                    request.getRequestURI(), response.getStatus(), System.currentTimeMillis() - started);
        } catch (ServletException | IOException exception) {
            log.error("Warehouse request failed method={} uri={} durationMs={}", request.getMethod(),
                    request.getRequestURI(), System.currentTimeMillis() - started, exception);
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Warehouse request failed method={} uri={} durationMs={}", request.getMethod(),
                    request.getRequestURI(), System.currentTimeMillis() - started, exception);
            throw exception;
        }
    }
}
