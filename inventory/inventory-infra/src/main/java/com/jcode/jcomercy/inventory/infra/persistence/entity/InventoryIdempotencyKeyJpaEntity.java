package com.jcode.jcomercy.inventory.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "idempotency_key",
        schema = "inventory",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_idempotency_scope_key",
                columnNames = {"company_id", "actor_user_id", "idempotency_key"}
        )
)
public class InventoryIdempotencyKeyJpaEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false, length = 100)
    private String companyId;

    @Column(name = "actor_user_id", nullable = false, length = 100)
    private String actorUserId;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryIdempotencyKeyJpaEntity() {}

    public InventoryIdempotencyKeyJpaEntity(UUID id, String companyId, String actorUserId, String idempotencyKey,
                                             String requestHash, Integer responseStatus, String responseBody,
                                             String status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = companyId;
        this.actorUserId = actorUserId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getCompanyId() { return companyId; }
    public String getActorUserId() { return actorUserId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void complete(int responseStatus, String responseBody) {
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.status = "COMPLETED";
        this.updatedAt = Instant.now();
    }
}
