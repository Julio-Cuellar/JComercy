package com.jcode.jcomercy.companymanagement.infra.persistence.entity;

import com.jcode.jcomercy.companymanagement.domain.model.company.Company;
import com.jcode.jcomercy.companymanagement.domain.model.company.CompanyStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "company", schema = "company_management")
public class CompanyJpaEntity {
    @Id
    private UUID id;

    @Column(name = "public_company_id", nullable = false, unique = true, length = 64)
    private String publicCompanyId;

    @Column(name = "public_account_id", nullable = false, length = 64)
    private String publicAccountId;

    @Column(name = "legal_name", nullable = false, length = 255)
    private String legalName;

    @Column(name = "trade_name", nullable = false, length = 255)
    private String tradeName;

    @Column(name = "rfc", nullable = false, length = 13)
    private String rfc;

    @Column(name = "tax_regime", nullable = false, length = 100)
    private String taxRegime;

    @Column(name = "incorporation_date", nullable = false)
    private java.time.LocalDate incorporationDate;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "website", nullable = false, length = 255)
    private String website;

    @Column(name = "logo_url", nullable = false, length = 500)
    private String logoUrl;

    @Embedded
    private DomicilioFiscalJpaEmbeddable fiscalAddress;

    @Column(name = "status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private CompanyStatus status;

    @Column(name = "preferences_json", nullable = false, columnDefinition = "TEXT")
    private String preferencesJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public CompanyJpaEntity() {}

    public static CompanyJpaEntity fromDomain(Company domain) {
        CompanyJpaEntity entity = new CompanyJpaEntity();
        entity.id = domain.getId();
        entity.publicCompanyId = domain.getPublicCompanyId();
        entity.publicAccountId = domain.getPublicAccountId();
        entity.legalName = domain.getLegalName();
        entity.tradeName = domain.getTradeName();
        entity.rfc = domain.getRfc();
        entity.taxRegime = domain.getTaxRegime();
        entity.incorporationDate = domain.getIncorporationDate();
        entity.phone = domain.getPhone(); entity.email = domain.getEmail(); entity.website = domain.getWebsite(); entity.logoUrl = domain.getLogoUrl();
        entity.fiscalAddress = DomicilioFiscalJpaEmbeddable.fromDomain(domain.getFiscalAddress());
        entity.status = domain.getStatus();
        entity.preferencesJson = domain.getPreferencesJson();
        entity.createdAt = domain.getCreatedAt();
        entity.updatedAt = domain.getUpdatedAt();
        entity.version = domain.getVersion();
        return entity;
    }

    public Company toDomain() {
        return new Company(id, publicCompanyId, publicAccountId, legalName, tradeName, rfc, taxRegime, incorporationDate, phone, email,
            website, logoUrl, fiscalAddress.toDomain(), status, preferencesJson, createdAt, updatedAt, version);
    }

    // Getters & Setters
    public UUID getId() { return id; }
    public String getPublicCompanyId() { return publicCompanyId; }
    public String getPublicAccountId() { return publicAccountId; }
    public String getLegalName() { return legalName; }
    public String getTradeName() { return tradeName; }
    public String getRfc() { return rfc; }
    public CompanyStatus getStatus() { return status; }
    public String getPreferencesJson() { return preferencesJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
