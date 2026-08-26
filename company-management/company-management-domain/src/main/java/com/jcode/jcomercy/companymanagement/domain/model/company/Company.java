package com.jcode.jcomercy.companymanagement.domain.model.company;

import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Company {
    private final UUID id;
    private final String publicCompanyId;
    private final String publicAccountId;
    private String legalName;
    private String tradeName;
    private String taxId;
    private CompanyStatus status;
    private String preferencesJson;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public Company(UUID id, String publicCompanyId, String publicAccountId, String legalName, String tradeName, String taxId, CompanyStatus status, String preferencesJson, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id no puede ser nulo");
        this.publicCompanyId = Objects.requireNonNull(publicCompanyId, "publicCompanyId no puede ser nulo");
        this.publicAccountId = Objects.requireNonNull(publicAccountId, "publicAccountId no puede ser nulo");
        this.legalName = validateLegalName(legalName);
        this.tradeName = tradeName != null ? tradeName.trim() : legalName;
        this.taxId = taxId != null ? taxId.trim() : "";
        this.status = Objects.requireNonNull(status, "status no puede ser nulo");
        this.preferencesJson = preferencesJson != null ? preferencesJson : "{}";
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt no puede ser nulo");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt no puede ser nulo");
        this.version = version;
    }

    public static Company create(String publicAccountId, String legalName, String tradeName, String taxId) {
        Instant now = Instant.now();
        String publicId = "com_" + UUID.randomUUID().toString().replace("-", "");
        return new Company(UUID.randomUUID(), publicId, publicAccountId, legalName, tradeName, taxId, CompanyStatus.CONFIGURING, "{}", now, now, 0L);
    }

    private String validateLegalName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new DomainRuleViolationException("La razón social de la empresa no puede estar vacía.");
        }
        return name.trim();
    }

    public void updateProfile(String legalName, String tradeName, String taxId) {
        if (this.status == CompanyStatus.DELETED) {
            throw new DomainRuleViolationException("No se puede actualizar una empresa eliminada.");
        }
        this.legalName = validateLegalName(legalName);
        if (tradeName != null && !tradeName.trim().isEmpty()) {
            this.tradeName = tradeName.trim();
        }
        if (taxId != null) {
            this.taxId = taxId.trim();
        }
        this.updatedAt = Instant.now();
    }

    public void completeConfiguration() {
        if (this.status != CompanyStatus.CONFIGURING) {
            throw new DomainRuleViolationException("La empresa no está en estado CONFIGURING.");
        }
        this.status = CompanyStatus.READY_FOR_ACTIVATION;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        if (this.status == CompanyStatus.DELETED) {
            throw new DomainRuleViolationException("No se puede activar una empresa eliminada.");
        }
        this.status = CompanyStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void suspend(String reason) {
        if (this.status == CompanyStatus.DELETED) {
            throw new DomainRuleViolationException("No se puede suspender una empresa eliminada.");
        }
        this.status = CompanyStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        if (this.status != CompanyStatus.SUSPENDED) {
            throw new DomainRuleViolationException("Solo se pueden reactivar empresas suspendidas.");
        }
        this.status = CompanyStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void requestDeletion() {
        this.status = CompanyStatus.DELETION_REQUESTED;
        this.updatedAt = Instant.now();
    }

    public void delete() {
        this.status = CompanyStatus.DELETED;
        this.updatedAt = Instant.now();
    }

    public void updatePreferences(String preferencesJson) {
        this.preferencesJson = preferencesJson != null ? preferencesJson : "{}";
        this.updatedAt = Instant.now();
    }

    // Getters
    public UUID getId() { return id; }
    public String getPublicCompanyId() { return publicCompanyId; }
    public String getPublicAccountId() { return publicAccountId; }
    public String getLegalName() { return legalName; }
    public String getTradeName() { return tradeName; }
    public String getTaxId() { return taxId; }
    public CompanyStatus getStatus() { return status; }
    public String getPreferencesJson() { return preferencesJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
