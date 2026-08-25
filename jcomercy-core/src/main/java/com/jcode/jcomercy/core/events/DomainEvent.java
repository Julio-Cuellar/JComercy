package com.jcode.jcomercy.core.events;

import com.jcode.jcomercy.core.identifiers.CausationId;
import com.jcode.jcomercy.core.identifiers.CorrelationId;
import com.jcode.jcomercy.core.identifiers.EventId;
import com.jcode.jcomercy.core.identifiers.PublicAccountId;
import com.jcode.jcomercy.core.identifiers.PublicBranchId;
import com.jcode.jcomercy.core.identifiers.PublicCompanyId;
import com.jcode.jcomercy.core.identifiers.PublicUserId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record DomainEvent<T>(
        EventId eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String producerModule,
        CorrelationId correlationId,
        Optional<CausationId> causationId,
        Optional<PublicAccountId> publicAccountId,
        Optional<PublicCompanyId> publicCompanyId,
        Optional<PublicBranchId> publicBranchId,
        Optional<PublicUserId> actorPublicUserId,
        T payload) {

    public DomainEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be positive");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (producerModule == null || producerModule.isBlank()) {
            throw new IllegalArgumentException("producerModule must not be blank");
        }
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        causationId = Objects.requireNonNull(causationId, "causationId must not be null");
        publicAccountId = Objects.requireNonNull(publicAccountId, "publicAccountId must not be null");
        publicCompanyId = Objects.requireNonNull(publicCompanyId, "publicCompanyId must not be null");
        publicBranchId = Objects.requireNonNull(publicBranchId, "publicBranchId must not be null");
        actorPublicUserId = Objects.requireNonNull(actorPublicUserId, "actorPublicUserId must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
    }
}
