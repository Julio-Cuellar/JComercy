package com.jcode.jcomercy.companymanagement.infra.persistence.entity;

import com.jcode.jcomercy.companymanagement.domain.model.branch.Branch;
import com.jcode.jcomercy.companymanagement.domain.model.branch.BranchStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "branch", schema = "company_management")
public class BranchJpaEntity {
    @Id
    private UUID id;

    @Column(name = "public_branch_id", nullable = false, unique = true, length = 64)
    private String publicBranchId;

    @Column(name = "public_company_id", nullable = false, length = 64)
    private String publicCompanyId;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "is_head_office", nullable = false)
    private boolean isHeadOffice;

    @Column(name = "status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private BranchStatus status;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public BranchJpaEntity() {}

    public static BranchJpaEntity fromDomain(Branch domain) {
        BranchJpaEntity entity = new BranchJpaEntity();
        entity.id = domain.getId();
        entity.publicBranchId = domain.getPublicBranchId();
        entity.publicCompanyId = domain.getPublicCompanyId();
        entity.code = domain.getCode();
        entity.name = domain.getName();
        entity.isHeadOffice = domain.isHeadOffice();
        entity.status = domain.getStatus();
        entity.timeZone = domain.getTimeZone();
        entity.createdAt = domain.getCreatedAt();
        entity.updatedAt = domain.getUpdatedAt();
        entity.version = domain.getVersion();
        return entity;
    }

    public Branch toDomain() {
        return new Branch(id, publicBranchId, publicCompanyId, code, name, isHeadOffice, status, timeZone, createdAt, updatedAt, version);
    }

    // Getters & Setters
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
