package com.jcode.jcomercy.sales.infra;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/** Tokens de empresa de ejemplo, firmados con la misma llave local con que arranca el módulo en pruebas. */
public final class SalesTokens {

    private static final String SECRET = "local-only-change-me-012345678901234567890123456789";

    private SalesTokens() {
    }

    /** Un token de esa empresa, con esas sucursales y esos permisos separados por espacio. */
    public static String bearer(String companyId, List<String> branchIds, String permissions) {
        return bearer("usuario-ventas-test", companyId, branchIds, permissions);
    }

    public static String bearer(String subject, String companyId, List<String> branchIds, String permissions) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
            .subject(subject)
            .issuer("jcomercy")
            .claim("companyIds", List.of(companyId))
            .claim("branchIds", branchIds)
            .claim("permissions", permissions)
            .expiration(new Date(System.currentTimeMillis() + 3600_000))
            .signWith(key)
            .compact();
    }
}
