package com.jcode.jcomercy.companymanagement.application.dto;

import java.time.Instant;

/** El resultado de suspender, reactivar o revocar algo: qué es, en qué estado quedó y cuándo cambió. */
public record LifecycleDto(String id, String status, Instant changedAt, String correlationId) { }
