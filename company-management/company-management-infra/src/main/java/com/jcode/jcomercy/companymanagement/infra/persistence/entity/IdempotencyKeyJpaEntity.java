package com.jcode.jcomercy.companymanagement.infra.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_key", schema = "company_management",
    uniqueConstraints = @UniqueConstraint(name = "ux_idempotency_scope_key", columnNames = {"operation", "idempotency_key"}))
public class IdempotencyKeyJpaEntity {
    @Id private UUID id;
    @Column(nullable=false, length=120) private String operation;
    @Column(name="idempotency_key", nullable=false, length=120) private String key;
    @Column(nullable=false, length=20) private String status;
    @Column(nullable=false) private Instant createdAt;
    public IdempotencyKeyJpaEntity() {}
    public static IdempotencyKeyJpaEntity create(String operation, String key) {
        IdempotencyKeyJpaEntity e = new IdempotencyKeyJpaEntity(); e.id=UUID.randomUUID(); e.operation=operation;
        e.key=key; e.status="CLAIMED"; e.createdAt=Instant.now(); return e;
    }
    public UUID getId(){return id;} public String getOperation(){return operation;} public String getKey(){return key;}
}
