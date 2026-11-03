package com.jcode.jcomercy.companymanagement.infra.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenService {
    private final SecretKey key;
    private final String issuer;
    private final long expirationMilliseconds;

    public JwtTokenService(
        @Value("${security.jwt.secret}") String secret,
        @Value("${security.jwt.issuer}") String issuer,
        @Value("${security.jwt.expiration-ms:3600000}") long expirationMilliseconds
    ) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("security.jwt.secret debe tener al menos 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.expirationMilliseconds = expirationMilliseconds;
    }

    public String issue(String subject, List<String> permissions) {
        return issue(subject, permissions, List.of(), List.of());
    }

    public String issue(String subject, List<String> permissions,
                        List<String> companyIds, List<String> branchIds) {
        return build(subject, null, permissions, companyIds, branchIds);
    }

    /** El token del inicio de sesión: dice quién eres y nada más. No nombra empresas, sucursales ni permisos. */
    public String issueAccountToken(String subject) {
        return build(subject, null, List.of(), List.of(), List.of());
    }

    /**
     * El token de una empresa: nombra solo esa empresa, con sus sucursales y los permisos que la persona tiene en ella.
     * Nunca junta las empresas de la persona, así los permisos de una no valen en otra.
     */
    public String issueCompanyToken(String subject, String companyId, List<String> permissions, List<String> branchIds) {
        return build(subject, companyId, permissions, List.of(companyId), branchIds);
    }

    private String build(String subject, String companyContext, List<String> permissions,
                         List<String> companyIds, List<String> branchIds) {
        Date now = new Date();
        var token = Jwts.builder()
            .subject(subject)
            .issuer(issuer)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expirationMilliseconds))
            .claim("permissions", String.join(" ", permissions))
            .claim("companyIds", List.copyOf(companyIds))
            .claim("branchIds", List.copyOf(branchIds));
        if (companyContext != null) {
            token.claim("ctx", companyContext);
        }
        return token.signWith(key).compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .requireIssuer(issuer)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
