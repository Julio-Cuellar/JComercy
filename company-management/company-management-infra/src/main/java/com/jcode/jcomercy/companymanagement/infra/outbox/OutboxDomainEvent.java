package com.jcode.jcomercy.companymanagement.infra.outbox;
public record OutboxDomainEvent(String eventId, String eventType, String schemaVersion, String payloadJson, String accountId, String companyId, String branchId) {}
