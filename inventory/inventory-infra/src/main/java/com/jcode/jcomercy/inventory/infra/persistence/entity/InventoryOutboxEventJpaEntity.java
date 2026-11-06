package com.jcode.jcomercy.inventory.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event", schema = "inventory")
public class InventoryOutboxEventJpaEntity {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "schema_version", nullable = false, length = 20)
    private String schemaVersion;

    @Column(name = "company_id", nullable = false, length = 100)
    private String companyId;

    @Column(name = "branch_id", length = 100)
    private String branchId;

    @Column(name = "actor_user_id", nullable = false, length = 100)
    private String actorUserId;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "sequence", nullable = false)
    private long sequence;

    protected InventoryOutboxEventJpaEntity() {}

    public InventoryOutboxEventJpaEntity(UUID id, UUID eventId, String eventType, String schemaVersion,
                                          String companyId, String branchId, String actorUserId,
                                          String correlationId, String payload, String status,
                                          Instant createdAt, long sequence) {
        this.id = id;
        this.sequence = sequence;
        this.eventId = eventId;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.companyId = companyId;
        this.branchId = branchId;
        this.actorUserId = actorUserId;
        this.correlationId = correlationId;
        this.payload = payload;
        this.status = status;
        this.createdAt = createdAt;
        this.retryCount = 0;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getSchemaVersion() { return schemaVersion; }
    public String getCompanyId() { return companyId; }
    public String getBranchId() { return branchId; }
    public String getActorUserId() { return actorUserId; }
    public String getCorrelationId() { return correlationId; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getRetryCount() { return retryCount; }
    public String getLastError() { return lastError; }

    public Instant getNextRetryAt() { return nextRetryAt; }
    public long getSequence() { return sequence; }

    public void markPublished() {
        this.status = "PUBLISHED";
        this.publishedAt = Instant.now();
        this.lastError = null;
        this.nextRetryAt = null;
    }

    /** El broker rechazó el evento o no se pudo armar: espera 5 s por intento (hasta 300 s) antes de volver a intentarlo. */
    public void markFailed(String error) {
        this.retryCount++;
        this.lastError = error;
        this.nextRetryAt = Instant.now().plusSeconds(Math.min(300, 5L * retryCount));
    }

    /**
     * El broker no respondió (caído, sin confirmar): cuenta el intento y deja el evento listo para el siguiente ciclo. Sin
     * espera propia: el ciclo ya se cortó, y esperar solo lo sacaría de orden frente a los que vienen detrás.
     */
    public void markBrokerUnavailable(String error) {
        this.retryCount++;
        this.lastError = error;
        this.nextRetryAt = null;
    }
}
