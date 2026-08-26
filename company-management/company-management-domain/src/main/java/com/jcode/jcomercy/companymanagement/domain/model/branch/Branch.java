package com.jcode.jcomercy.companymanagement.domain.model.branch;

import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Branch {
    private final UUID id;
    private final String publicBranchId;
    private final String publicCompanyId;
    private String code;
    private String name;
    private boolean isHeadOffice;
    private BranchStatus status;
    private String timeZone;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public Branch(UUID id, String publicBranchId, String publicCompanyId, String code, String name, boolean isHeadOffice, BranchStatus status, String timeZone, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id no puede ser nulo");
        this.publicBranchId = Objects.requireNonNull(publicBranchId, "publicBranchId no puede ser nulo");
        this.publicCompanyId = Objects.requireNonNull(publicCompanyId, "publicCompanyId no puede ser nulo");
        this.code = validateCode(code);
        this.name = validateName(name);
        this.isHeadOffice = isHeadOffice;
        this.status = Objects.requireNonNull(status, "status no puede ser nulo");
        this.timeZone = timeZone != null ? timeZone : "America/Mexico_City";
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt no puede ser nulo");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt no puede ser nulo");
        this.version = version;
    }

    public static Branch create(String publicCompanyId, String code, String name, boolean isHeadOffice) {
        Instant now = Instant.now();
        String publicId = "brn_" + UUID.randomUUID().toString().replace("-", "");
        return new Branch(UUID.randomUUID(), publicId, publicCompanyId, code, name, isHeadOffice, BranchStatus.CONFIGURING, "America/Mexico_City", now, now, 0L);
    }

    private String validateCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new DomainRuleViolationException("El código de la sucursal no puede estar vacío.");
        }
        return code.trim().toUpperCase();
    }

    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new DomainRuleViolationException("El nombre de la sucursal no puede estar vacío.");
        }
        return name.trim();
    }

    public void updateProfile(String name, String timeZone) {
        if (this.status == BranchStatus.DELETED) {
            throw new DomainRuleViolationException("No se puede actualizar una sucursal eliminada.");
        }
        this.name = validateName(name);
        if (timeZone != null && !timeZone.trim().isEmpty()) {
            this.timeZone = timeZone.trim();
        }
        this.updatedAt = Instant.now();
    }

    public void completeConfiguration() {
        if (this.status != BranchStatus.CONFIGURING) {
            throw new DomainRuleViolationException("La sucursal no está en estado CONFIGURING.");
        }
        this.status = BranchStatus.READY_FOR_ACTIVATION;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        if (this.status == BranchStatus.DELETED) {
            throw new DomainRuleViolationException("No se puede activar una sucursal eliminada.");
        }
        this.status = BranchStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void requireCoverage() {
        this.status = BranchStatus.PENDING_PLAN;
        this.updatedAt = Instant.now();
    }

    public void suspend(String reason) {
        if (this.status == BranchStatus.DELETED) {
            throw new DomainRuleViolationException("No se puede suspender una sucursal eliminada.");
        }
        this.status = BranchStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        if (this.status != BranchStatus.SUSPENDED && this.status != BranchStatus.PENDING_PLAN) {
            throw new DomainRuleViolationException("Solo se pueden reactivar sucursales suspendidas o pendientes de plan.");
        }
        this.status = BranchStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void setHeadOffice(boolean headOffice) {
        this.isHeadOffice = headOffice;
        this.updatedAt = Instant.now();
    }

    public void delete() {
        this.status = BranchStatus.DELETED;
        this.updatedAt = Instant.now();
    }

    // Getters
    public UUID getId() { return id; }
    public String getPublicBranchId() { return publicBranchId; }
    public String getPublicCompanyId() { return publicCompanyId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isHeadOffice() { return isHeadOffice; }
    public BranchStatus getStatus() { return status; }
    public String getTimeZone() { return timeZone; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
