package com.jcode.jcomercy.inventory.infra.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.inventory.domain.port.InventoryOutboxPort;
import com.jcode.jcomercy.inventory.infra.persistence.entity.InventoryOutboxEventJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.InventoryOutboxEventSpringDataRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class InventoryOutboxAdapter implements InventoryOutboxPort {

    private final InventoryOutboxEventSpringDataRepository repository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public InventoryOutboxAdapter(InventoryOutboxEventSpringDataRepository repository, ObjectMapper objectMapper, JdbcTemplate jdbc) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    @Override
    public void publish(String eventType, String companyId, String branchId, String actorUserId, String correlationId, Object data) {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        String safeCorrelation = correlationId != null ? correlationId : "cor_" + UUID.randomUUID().toString().substring(0, 8);
        String safeActor = actorUserId != null ? actorUserId : "SYSTEM";

        // Solo el data va en la columna: el sobre (EventEnvelope de jcomercy-core) lo arma el despachador con las demás
        // columnas, igual que en company-management (ERR-026). Hasta aquí se guardaba el sobre completo.
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("No se pudo serializar el evento Outbox", e);
        }
        // Crece con el orden de registro; con huecos si una transacción se revierte.
        long sequence = jdbc.queryForObject("SELECT nextval('inventory.outbox_event_sequence')", Long.class);

        InventoryOutboxEventJpaEntity entity = new InventoryOutboxEventJpaEntity(
                id, eventId, eventType, "1.0", companyId, branchId, safeActor,
                safeCorrelation, payloadJson, "PENDING", now, sequence
        );

        repository.save(entity);
    }
}
