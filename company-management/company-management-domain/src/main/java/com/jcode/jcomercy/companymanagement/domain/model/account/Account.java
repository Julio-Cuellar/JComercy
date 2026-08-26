package com.jcode.jcomercy.companymanagement.domain.model.account;

import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Account {
    private final UUID id;
    private final String publicAccountId;
    private String name;
    private AccountStatus status;
    private final String ownerUserId;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public Account(UUID id, String publicAccountId, String name, AccountStatus status, String ownerUserId, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id no puede ser nulo");
        this.publicAccountId = Objects.requireNonNull(publicAccountId, "publicAccountId no puede ser nulo");
        this.name = validateName(name);
        this.status = Objects.requireNonNull(status, "status no puede ser nulo");
        this.ownerUserId = Objects.requireNonNull(ownerUserId, "ownerUserId no puede ser nulo");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt no puede ser nulo");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt no puede ser nulo");
        this.version = version;
    }

    public static Account create(String name, String ownerUserId) {
        Instant now = Instant.now();
        String publicId = "acc_" + UUID.randomUUID().toString().replace("-", "");
        return new Account(UUID.randomUUID(), publicId, name, AccountStatus.ACTIVE, ownerUserId, now, now, 0L);
    }

    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new DomainRuleViolationException("El nombre de la cuenta no puede estar vacío.");
        }
        return name.trim();
    }

    public void update(String name) {
        if (this.status == AccountStatus.DEACTIVATED) {
            throw new DomainRuleViolationException("No se puede actualizar una cuenta desactivada.");
        }
        this.name = validateName(name);
        this.updatedAt = Instant.now();
    }

    public void suspend(String reason) {
        if (this.status == AccountStatus.DEACTIVATED) {
            throw new DomainRuleViolationException("No se puede suspender una cuenta desactivada.");
        }
        this.status = AccountStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        if (this.status != AccountStatus.SUSPENDED) {
            throw new DomainRuleViolationException("Solo se pueden reactivar cuentas suspendidas.");
        }
        this.status = AccountStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.status = AccountStatus.DEACTIVATED;
        this.updatedAt = Instant.now();
    }

    // Getters
    public UUID getId() { return id; }
    public String getPublicAccountId() { return publicAccountId; }
    public String getName() { return name; }
    public AccountStatus getStatus() { return status; }
    public String getOwnerUserId() { return ownerUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
