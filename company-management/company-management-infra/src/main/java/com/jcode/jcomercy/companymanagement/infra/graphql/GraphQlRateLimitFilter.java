package com.jcode.jcomercy.companymanagement.infra.graphql;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
@Component
public class GraphQlRateLimitFilter extends OncePerRequestFilter {

    private final int limit;
    private final GraphQlRateLimiter rateLimiter;

    public GraphQlRateLimitFilter(@Value("${graphql.security.rate-limit-per-minute:60}") int limit,
                                  GraphQlRateLimiter rateLimiter) {
        this.limit = limit;
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"/graphql/v1".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!rateLimiter.tryAcquire(request.getRemoteAddr(), limit, 60)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            return;
        }
        chain.doFilter(request, response);
    }

}
