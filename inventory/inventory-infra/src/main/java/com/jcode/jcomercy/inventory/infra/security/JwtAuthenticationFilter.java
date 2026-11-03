package com.jcode.jcomercy.inventory.infra.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenService tokenService;

    public JwtAuthenticationFilter(JwtTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            log.debug("Petición sin JWT method={} uri={}", request.getMethod(), request.getRequestURI());
        } else {
            try {
                Claims claims = tokenService.parse(authorization.substring(7));
                if (claims.getSubject() != null && !claims.getSubject().isBlank()) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            claims.getSubject(), null, authorities(claims));
                    authentication.setDetails(new JwtContext(claims.getSubject(),
                            JwtContext.claimValues(claims.get("companyIds")),
                            JwtContext.claimValues(claims.get("branchIds"))));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.info("JWT validado method={} uri={} subject={} companies={} branches={} permissions={}",
                            request.getMethod(), request.getRequestURI(), claims.getSubject(),
                            JwtContext.claimValues(claims.get("companyIds")).size(),
                            JwtContext.claimValues(claims.get("branchIds")).size(),
                            authorities(claims).size());
                } else {
                    log.warn("JWT rechazado: subject vacío method={} uri={}", request.getMethod(), request.getRequestURI());
                }
            } catch (RuntimeException exception) {
                log.warn("JWT inválido method={} uri={} reason={}", request.getMethod(), request.getRequestURI(),
                        exception.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private static List<SimpleGrantedAuthority> authorities(Claims claims) {
        Object permissions = claims.get("permissions");
        if (!(permissions instanceof String value) || value.isBlank()) return List.of();
        return Arrays.stream(value.split(" ")).filter(p -> !p.isBlank())
                .map(p -> new SimpleGrantedAuthority("PERM_" + p)).toList();
    }
}
