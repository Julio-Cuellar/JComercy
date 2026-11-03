package com.jcode.jcomercy.companymanagement.domain.model.company;

import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class Company {
    private final UUID id;
    private final String publicCompanyId;
    private final String publicAccountId;
    private String legalName;
    private String tradeName;
    private String rfc;
    private String taxRegime;
    private LocalDate incorporationDate;
    private String phone;
    private String email;
    private String website;
    private String logoUrl;
    private DomicilioFiscal fiscalAddress;
    private CompanyStatus status;
    private String preferencesJson;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public Company(UUID id, String publicCompanyId, String publicAccountId, String legalName, String tradeName,
                   String rfc, String taxRegime, LocalDate incorporationDate, String phone, String email,
                   String website, String logoUrl, DomicilioFiscal fiscalAddress, CompanyStatus status,
                   String preferencesJson, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id no puede ser nulo");
        this.publicCompanyId = Objects.requireNonNull(publicCompanyId, "publicCompanyId no puede ser nulo");
        this.publicAccountId = Objects.requireNonNull(publicAccountId, "publicAccountId no puede ser nulo");
        this.legalName = validateLegalName(legalName);
        this.tradeName = tradeName != null ? tradeName.trim() : legalName;
        this.rfc = required(rfc, "El RFC").toUpperCase();
        this.taxRegime = required(taxRegime, "El régimen fiscal");
        this.incorporationDate = Objects.requireNonNull(incorporationDate, "La fecha de constitución no puede ser nula");
        if (incorporationDate.isAfter(LocalDate.now())) throw new DomainRuleViolationException("La fecha de constitución no puede ser futura.");
        this.phone = required(phone, "El teléfono");
        this.email = required(email, "El correo electrónico").toLowerCase();
        this.website = optional(website);
        this.logoUrl = optional(logoUrl);
        this.fiscalAddress = Objects.requireNonNull(fiscalAddress, "El domicilio fiscal no puede ser nulo");
        this.status = Objects.requireNonNull(status, "status no puede ser nulo");
        this.preferencesJson = preferencesJson != null ? preferencesJson : "{}";
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt no puede ser nulo");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt no puede ser nulo");
        this.version = version;
    }

    public static Company create(String publicAccountId, String legalName, String tradeName, String rfc, String taxRegime,
                                 LocalDate incorporationDate, String phone, String email, String website, String logoUrl,
                                 DomicilioFiscal fiscalAddress) {
        Instant now = Instant.now();
        String publicId = "com_" + UUID.randomUUID().toString().replace("-", "");
        return new Company(UUID.randomUUID(), publicId, publicAccountId, legalName, tradeName, rfc, taxRegime, incorporationDate,
            phone, email, website, logoUrl, fiscalAddress, CompanyStatus.CONFIGURING, "{}", now, now, 0L);
    }

    private String validateLegalName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new DomainRuleViolationException("La razón social de la empresa no puede estar vacía.");
        }
        return name.trim();
    }

    private static String required(String value, String label) {
        if (value == null || value.trim().isEmpty()) throw new DomainRuleViolationException(label + " no puede estar vacío.");
        return value.trim();
    }

    private static String optional(String value) { return value == null ? "" : value.trim(); }

    public void updateProfile(String legalName, String tradeName, String rfc) {
        if (this.status == CompanyStatus.DELETED) {
            throw new DomainRuleViolationException("No se puede actualizar una empresa eliminada.");
        }
        this.legalName = validateLegalName(legalName);
        if (tradeName != null && !tradeName.trim().isEmpty()) {
            this.tradeName = tradeName.trim();
        }
        if (rfc != null) {
            this.rfc = rfc.trim().toUpperCase();
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
    public String getRfc() { return rfc; }
    public String getTaxRegime() { return taxRegime; }
    public LocalDate getIncorporationDate() { return incorporationDate; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getWebsite() { return website; }
    public String getLogoUrl() { return logoUrl; }
    public DomicilioFiscal getFiscalAddress() { return fiscalAddress; }
    public CompanyStatus getStatus() { return status; }
    public String getPreferencesJson() { return preferencesJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
