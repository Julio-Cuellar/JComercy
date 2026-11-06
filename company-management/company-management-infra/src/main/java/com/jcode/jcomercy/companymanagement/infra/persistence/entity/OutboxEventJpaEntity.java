package com.jcode.jcomercy.companymanagement.infra.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event", schema = "company_management")
public class OutboxEventJpaEntity {
    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "schema_version", nullable = false, length = 16)
    private String schemaVersion;

    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;

    @Column(name = "sequence", nullable = false)
    private long sequence;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "producer_module", nullable = false, length = 64)
    private String producerModule;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "causation_id", length = 64)
    private String causationId;

    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    @Column(name = "company_id", length = 64)
    private String companyId;

    @Column(name = "branch_id", length = 64)
    private String branchId;

    @Column(name = "actor_user_id", length = 64)
    private String actorUserId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public OutboxEventJpaEntity() {}

    public static OutboxEventJpaEntity create(
        String eventId,
        String eventType,
        String schemaVersion,
        long aggregateVersion,
        long sequence,
        Instant occurredAt,
        String producerModule,
        String correlationId,
        String causationId,
        String accountId,
        String companyId,
        String branchId,
        String actorUserId,
        String payloadJson
    ) {
        Instant now = Instant.now();
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.id = UUID.randomUUID();
        entity.eventId = eventId;
        entity.eventType = eventType;
        entity.schemaVersion = schemaVersion;
        entity.aggregateVersion = aggregateVersion;
        entity.sequence = sequence;
        entity.occurredAt = occurredAt != null ? occurredAt : now;
        entity.producerModule = producerModule != null ? producerModule : "company_management";
        entity.correlationId = correlationId;
        entity.causationId = causationId;
        entity.accountId = accountId;
        entity.companyId = companyId;
        entity.branchId = branchId;
        entity.actorUserId = actorUserId;
        entity.status = "PENDING";
        entity.retryCount = 0;
        entity.payloadJson = payloadJson;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    // Getters & Setters
    public UUID getId() { return id; }
    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getSchemaVersion() { return schemaVersion; }
    public long getAggregateVersion() { return aggregateVersion; }
    public long getSequence() { return sequence; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getProducerModule() { return producerModule; }
    public String getCorrelationId() { return correlationId; }
    public String getCausationId() { return causationId; }
    public String getAccountId() { return accountId; }
    public String getCompanyId() { return companyId; }
    public String getBranchId() { return branchId; }
    public String getActorUserId() { return actorUserId; }
    public String getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getErrorMessage() { return errorMessage; }
    public String getPayloadJson() { return payloadJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void markPublished() { status = "PUBLISHED"; publishedAt = Instant.now(); updatedAt = Instant.now(); errorMessage = null; }
    /** El broker rechazó el evento o no se pudo armar: espera 5 s por intento (hasta 300 s) antes de volver a intentarlo. */
    public void markFailed(String message) { status = "PENDING"; retryCount++; errorMessage = message; nextRetryAt = Instant.now().plusSeconds(Math.min(300, 5L * retryCount)); updatedAt = Instant.now(); }

    /**
     * El broker no respondió (caído, sin confirmar): cuenta el intento y deja el evento listo para el siguiente ciclo. Sin
     * espera propia: el ciclo ya se cortó, y esperar solo lo sacaría de orden frente a los que vienen detrás.
     */
    public void markBrokerUnavailable(String message) { status = "PENDING"; retryCount++; errorMessage = message; nextRetryAt = null; updatedAt = Instant.now(); }
}
