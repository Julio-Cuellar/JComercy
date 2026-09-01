package com.jclinical.core.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CashExpenseRegisteredEvent(
        UUID eventId,
        UUID clinicId,
        UUID cashExpenseId,
        UUID cashSessionId,
        String concept,
        BigDecimal amount,
        LocalDateTime occurredAt
) {}
