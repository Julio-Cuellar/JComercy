package com.jcode.jcomercy.companymanagement.infra.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.companymanagement.domain.port.EventOutboxPort;
import com.jcode.jcomercy.companymanagement.infra.persistence.entity.OutboxEventJpaEntity;
import com.jcode.jcomercy.companymanagement.infra.persistence.repository.OutboxSpringDataRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class JpaOutboxAdapter implements EventOutboxPort {
    private final OutboxSpringDataRepository repository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public JpaOutboxAdapter(OutboxSpringDataRepository repository, ObjectMapper objectMapper, JdbcTemplate jdbc) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void recordEvent(
        String eventType,
        String schemaVersion,
        long aggregateVersion,
        String producerModule,
        String correlationId,
        String causationId,
        String accountId,
        String companyId,
        String branchId,
        String actorUserId,
        Object dataPayload
    ) {
        try {
            String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
            String payloadJson = objectMapper.writeValueAsString(dataPayload);
            // Crece con el orden de registro (ERR-024); con huecos si una transacción se revierte.
            long sequence = jdbc.queryForObject("SELECT nextval('company_management.outbox_event_sequence')", Long.class);

            OutboxEventJpaEntity entity = OutboxEventJpaEntity.create(
                eventId,
                eventType,
                schemaVersion,
                aggregateVersion,
                sequence,
                Instant.now(),
                producerModule,
                correlationId,
                causationId,
                accountId,
                companyId,
                branchId,
                actorUserId,
                payloadJson
            );

            repository.save(entity);
        } catch (Exception e) {
            throw new RuntimeException("Error al registrar evento en outbox: " + e.getMessage(), e);
        }
    }
}
