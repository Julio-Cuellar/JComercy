package com.jcode.jcomercy.inventory.infra.idempotency;

import com.jcode.jcomercy.inventory.infra.persistence.entity.InventoryIdempotencyKeyJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.InventoryIdempotencyKeySpringDataRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class InventoryIdempotencyService {

    private final InventoryIdempotencyKeySpringDataRepository repository;

    public InventoryIdempotencyService(InventoryIdempotencyKeySpringDataRepository repository) {
        this.repository = repository;
    }

    public record ClaimResult(
            boolean isCompleted,
            UUID id,
            Integer cachedStatus,
            String cachedResponseBody,
            boolean isConflict
    ) {}

    public ClaimResult claim(String companyId, String actorUserId, String idempotencyKey, String requestBody) {
        String hash = computeHash(requestBody);

        Optional<InventoryIdempotencyKeyJpaEntity> existing = repository.findByCompanyIdAndActorUserIdAndIdempotencyKey(
                companyId, actorUserId, idempotencyKey
        );

        if (existing.isPresent()) {
            InventoryIdempotencyKeyJpaEntity entity = existing.get();
            if (!entity.getRequestHash().equals(hash)) {
                return new ClaimResult(false, entity.getId(), null, null, true);
            }
            if ("COMPLETED".equals(entity.getStatus())) {
                return new ClaimResult(true, entity.getId(), entity.getResponseStatus(), entity.getResponseBody(), false);
            } else {
                // Ongoing concurrent request
                return new ClaimResult(false, entity.getId(), null, null, true);
            }
        }

        UUID newId = UUID.randomUUID();
        Instant now = Instant.now();
        InventoryIdempotencyKeyJpaEntity entity = new InventoryIdempotencyKeyJpaEntity(
                newId, companyId, actorUserId, idempotencyKey, hash, null, null, "PENDING", now, now
        );

        try {
            repository.saveAndFlush(entity);
            return new ClaimResult(false, newId, null, null, false);
        } catch (DataIntegrityViolationException e) {
            // Concurrent insertion caught by unique constraint
            Optional<InventoryIdempotencyKeyJpaEntity> concurrent = repository.findByCompanyIdAndActorUserIdAndIdempotencyKey(
                    companyId, actorUserId, idempotencyKey
            );
            if (concurrent.isPresent()) {
                InventoryIdempotencyKeyJpaEntity c = concurrent.get();
                if (!c.getRequestHash().equals(hash)) {
                    return new ClaimResult(false, c.getId(), null, null, true);
                }
                if ("COMPLETED".equals(c.getStatus())) {
                    return new ClaimResult(true, c.getId(), c.getResponseStatus(), c.getResponseBody(), false);
                }
            }
            return new ClaimResult(false, null, null, null, true);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID id, int status, String body) {
        if (id == null) return;
        repository.findById(id).ifPresent(entity -> {
            String sanitizedBody = sanitizeBody(body);
            entity.complete(status, sanitizedBody);
            repository.saveAndFlush(entity);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(UUID id) {
        if (id == null) return;
        repository.findById(id).ifPresent(entity -> {
            if ("PENDING".equals(entity.getStatus())) {
                repository.delete(entity);
                repository.flush();
            }
        });
    }

    private String computeHash(String input) {
        if (input == null) input = "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private String sanitizeBody(String body) {
        if (body == null) return null;
        // Clean out sensitive JWT tokens or passwords if present
        return body.replaceAll("\"token\"\\s*:\\s*\"[^\"]+\"", "\"token\":\"***\"")
                .replaceAll("\"password\"\\s*:\\s*\"[^\"]+\"", "\"password\":\"***\"");
    }
}
