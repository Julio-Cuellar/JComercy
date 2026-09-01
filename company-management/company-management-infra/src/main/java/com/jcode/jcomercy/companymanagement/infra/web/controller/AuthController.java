package com.jcode.jcomercy.companymanagement.infra.web.controller;

import com.jcode.jcomercy.companymanagement.infra.security.JwtTokenService;
import com.jcode.jcomercy.companymanagement.infra.security.PermissionResolver;
import com.jcode.jcomercy.companymanagement.domain.model.account.Account;
import com.jcode.jcomercy.companymanagement.domain.port.AccountRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.PasswordHasher;
import com.jcode.jcomercy.companymanagement.infra.persistence.entity.PasswordResetTokenJpaEntity;
import com.jcode.jcomercy.companymanagement.infra.persistence.entity.RefreshTokenJpaEntity;
import com.jcode.jcomercy.companymanagement.infra.persistence.repository.PasswordResetTokenSpringDataRepository;
import com.jcode.jcomercy.companymanagement.infra.persistence.repository.RefreshTokenSpringDataRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final JwtTokenService tokenService;
    private final AccountRepositoryPort accountRepository;
    private final PasswordHasher passwordHasher;
    private final RefreshTokenSpringDataRepository refreshTokens;
    private final PasswordResetTokenSpringDataRepository resetTokens;
    private final PermissionResolver permissionResolver;
    private final long expirationMilliseconds;

    public AuthController(
        JwtTokenService tokenService,
        AccountRepositoryPort accountRepository,
        PasswordHasher passwordHasher,
        RefreshTokenSpringDataRepository refreshTokens,
        PasswordResetTokenSpringDataRepository resetTokens,
        PermissionResolver permissionResolver,
        @org.springframework.beans.factory.annotation.Value("${security.jwt.expiration-ms:3600000}") long expirationMilliseconds
    ) {
        this.tokenService = tokenService;
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.permissionResolver = permissionResolver;
        this.expirationMilliseconds = expirationMilliseconds;
    }

    public record LoginRequest(String email, String password) { }
    public record LoginResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) { }
    public record RefreshRequest(String refreshToken) { }
    public record PasswordRecoveryRequest(String email) { }
    public record PasswordResetRequest(String token, String newPassword) { }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        Account account = request.email() == null ? null : accountRepository.findByOwnerEmail(request.email().trim().toLowerCase()).orElse(null);
        boolean validPassword = account != null && request.password() != null
            && passwordHasher.matches(request.password(), account.getOwner().getPasswordHash());
        if (!validPassword) {
            log.warn("Login rechazado para email={}", request.email());
            return ResponseEntity.status(401).build();
        }
        List<String> permissions = permissionResolver.resolve(account.getPublicAccountId());
        List<String> companyIds = permissionResolver.resolveCompanyIds(account.getPublicAccountId());
        List<String> branchIds = permissionResolver.resolveBranchIds(account.getPublicAccountId());
        String refreshToken = randomToken();
        refreshTokens.save(RefreshTokenJpaEntity.create(hash(refreshToken), account.getPublicAccountId(),
            Instant.now().plusSeconds(60L * 60 * 24 * 30)));
        log.info("Login exitoso accountId={} companies={} branches={}", account.getPublicAccountId(), companyIds.size(), branchIds.size());
        return ResponseEntity.ok(new LoginResponse(
            tokenService.issue(account.getPublicAccountId(), permissions, companyIds, branchIds),
            refreshToken, "Bearer", expirationMilliseconds / 1000));
    }

    @PostMapping("/refresh") @Transactional
    public ResponseEntity<LoginResponse> refresh(@RequestBody RefreshRequest request) {
        if (request == null || request.refreshToken() == null) return ResponseEntity.status(401).build();
        var stored = refreshTokens.findByTokenHash(hash(request.refreshToken())).orElse(null);
        if (stored == null || stored.getRevokedAt() != null || stored.getExpiresAt().isBefore(Instant.now())) {
            log.warn("Refresh token rechazado");
            return ResponseEntity.status(401).build();
        }
        Account account = accountRepository.findByPublicAccountId(stored.getAccountId()).orElse(null);
        if (account == null) {
            log.warn("Refresh token sin cuenta válida");
            return ResponseEntity.status(401).build();
        }
        stored.revoke(); refreshTokens.save(stored);
        String next = randomToken();
        refreshTokens.save(RefreshTokenJpaEntity.create(hash(next), account.getPublicAccountId(), Instant.now().plusSeconds(60L * 60 * 24 * 30)));
        String accountId = account.getPublicAccountId();
        log.info("Refresh token rotado accountId={}", accountId);
        return ResponseEntity.ok(new LoginResponse(tokenService.issue(accountId,
            permissionResolver.resolve(accountId),
            permissionResolver.resolveCompanyIds(accountId),
            permissionResolver.resolveBranchIds(accountId)), next, "Bearer", expirationMilliseconds / 1000));
    }

    @PostMapping("/password-recovery") @Transactional
    public ResponseEntity<Void> requestRecovery(@RequestBody PasswordRecoveryRequest request) {
        Account account = request == null || request.email() == null ? null : accountRepository.findByOwnerEmail(request.email().trim().toLowerCase()).orElse(null);
        if (account != null) {
            String token = randomToken();
            resetTokens.save(PasswordResetTokenJpaEntity.create(hash(token), account.getPublicAccountId(), Instant.now().plusSeconds(900)));
            log.info("Solicitud de recuperación procesada accountId={}", account.getPublicAccountId());
        }
        else log.info("Solicitud de recuperación procesada sin revelar si el correo existe");
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-recovery/confirm") @Transactional
    public ResponseEntity<Void> confirmRecovery(@RequestBody PasswordResetRequest request) {
        if (request == null || request.token() == null || request.newPassword() == null || request.newPassword().length() < 8) return ResponseEntity.badRequest().build();
        var token = resetTokens.findByTokenHash(hash(request.token())).orElse(null);
        if (token == null || token.getUsedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            log.warn("Confirmación de recuperación rechazada por token inválido o expirado");
            return ResponseEntity.status(401).build();
        }
        Account account = accountRepository.findByPublicAccountId(token.getAccountId()).orElse(null);
        if (account == null) return ResponseEntity.status(401).build();
        accountRepository.save(new Account(account.getId(), account.getPublicAccountId(), account.getName(), account.getStatus(), account.getOwner().withPasswordHash(passwordHasher.hash(request.newPassword())), account.getCreatedAt(), Instant.now(), account.getVersion()));
        token.use(); resetTokens.save(token); log.info("Contraseña actualizada accountId={}", account.getPublicAccountId()); return ResponseEntity.noContent().build();
    }

    private static String randomToken() { byte[] b = new byte[32]; new SecureRandom().nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
