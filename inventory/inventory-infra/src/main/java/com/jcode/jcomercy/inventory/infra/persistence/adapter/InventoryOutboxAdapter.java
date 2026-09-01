package com.jcode.jcomercy.inventory.infra.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.inventory.domain.port.InventoryOutboxPort;
import com.jcode.jcomercy.inventory.infra.persistence.entity.InventoryOutboxEventJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.InventoryOutboxEventSpringDataRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class InventoryOutboxAdapter implements InventoryOutboxPort {

    private final InventoryOutboxEventSpringDataRepository repository;
    private final ObjectMapper objectMapper;

    public InventoryOutboxAdapter(InventoryOutboxEventSpringDataRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(String eventType, String companyId, String branchId, String actorUserId, String correlationId, Object data) {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        String safeCorrelation = correlationId != null ? correlationId : "cor_" + UUID.randomUUID().toString().substring(0, 8);
        String safeActor = actorUserId != null ? actorUserId : "SYSTEM";

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("schemaVersion", "1.0");
        envelope.put("occurredAt", now.toString());
        envelope.put("correlationId", safeCorrelation);
        envelope.put("companyId", companyId);
        envelope.put("branchId", branchId);
        envelope.put("actorUserId", safeActor);
        envelope.put("data", data);

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("No se pudo serializar el evento Outbox", e);
        }

        InventoryOutboxEventJpaEntity entity = new InventoryOutboxEventJpaEntity(
                id, eventId, eventType, "1.0", companyId, branchId, safeActor,
                safeCorrelation, payloadJson, "PENDING", now
        );

        repository.save(entity);
    }
}
