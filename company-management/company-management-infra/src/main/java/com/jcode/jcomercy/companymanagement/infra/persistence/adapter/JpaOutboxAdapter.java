package com.jcode.jcomercy.companymanagement.infra.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.companymanagement.domain.port.EventOutboxPort;
import com.jcode.jcomercy.companymanagement.infra.persistence.entity.OutboxEventJpaEntity;
import com.jcode.jcomercy.companymanagement.infra.persistence.repository.OutboxSpringDataRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class JpaOutboxAdapter implements EventOutboxPort {
    private final OutboxSpringDataRepository repository;
    private final ObjectMapper objectMapper;

    public JpaOutboxAdapter(OutboxSpringDataRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void recordEvent(
        String eventType,
        String schemaVersion,
        long aggregateVersion,
        long sequence,
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
