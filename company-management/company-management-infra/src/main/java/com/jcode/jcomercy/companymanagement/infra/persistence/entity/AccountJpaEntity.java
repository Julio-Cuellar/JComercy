package com.jcode.jcomercy.companymanagement.infra.persistence.entity;

import com.jcode.jcomercy.companymanagement.domain.model.account.Account;
import com.jcode.jcomercy.companymanagement.domain.model.account.AccountStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
import java.time.LocalDate;

@Entity
@Table(name = "account", schema = "company_management")
public class AccountJpaEntity {
    @Id
    private UUID id;

    @Column(name = "public_account_id", nullable = false, unique = true, length = 64)
    private String publicAccountId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
    @JoinColumn(name = "owner_person_id", nullable = false, unique = true)
    private OwnerPersonJpaEntity owner;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public AccountJpaEntity() {}

    public static AccountJpaEntity fromDomain(Account domain) {
        AccountJpaEntity entity = new AccountJpaEntity();
        entity.id = domain.getId();
        entity.publicAccountId = domain.getPublicAccountId();
        entity.name = domain.getName();
        entity.status = domain.getStatus();
        entity.owner = OwnerPersonJpaEntity.fromDomain(domain.getOwner());
        entity.createdAt = domain.getCreatedAt();
        entity.updatedAt = domain.getUpdatedAt();
        entity.version = domain.getVersion();
        return entity;
    }

    public Account toDomain() {
        return new Account(id, publicAccountId, name, status, owner.toDomain(), createdAt, updatedAt, version);
    }

    // Getters & Setters
    public UUID getId() { return id; }
    public String getPublicAccountId() { return publicAccountId; }
    public String getName() { return name; }
    public AccountStatus getStatus() { return status; }
    public OwnerPersonJpaEntity getOwner() { return owner; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
