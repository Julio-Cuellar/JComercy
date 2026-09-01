package com.jclinical.core.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CashExpenseVoidedEvent(
        UUID eventId,
        UUID clinicId,
        UUID cashExpenseId,
        UUID cashSessionId,
        BigDecimal amount,
        String reason,
        LocalDateTime occurredAt
) {}
