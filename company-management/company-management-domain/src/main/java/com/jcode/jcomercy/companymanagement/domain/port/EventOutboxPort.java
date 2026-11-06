package com.jcode.jcomercy.companymanagement.domain.port;

public interface EventOutboxPort {
    void recordEvent(
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
    );
}
