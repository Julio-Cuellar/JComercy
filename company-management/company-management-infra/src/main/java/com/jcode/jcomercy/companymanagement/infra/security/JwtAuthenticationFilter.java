package com.jcode.jcomercy.companymanagement.infra.security;

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
import java.util.Collection;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService tokenService;

    public JwtAuthenticationFilter(JwtTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                Claims claims = tokenService.parse(authorization.substring(7));
                String subject = claims.getSubject();
                if (subject != null && !subject.isBlank()) {
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(subject, null, authorities(claims));
                    authentication.setDetails(new JwtContext(subject,
                        JwtContext.claimValues(claims.get("companyIds")),
                        JwtContext.claimValues(claims.get("branchIds"))));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private static Collection<SimpleGrantedAuthority> authorities(Claims claims) {
        Object permissions = claims.get("permissions");
        if (!(permissions instanceof String value) || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(" "))
            .filter(permission -> !permission.isBlank())
            .map(permission -> new SimpleGrantedAuthority("PERM_" + permission))
            .toList();
    }
}
