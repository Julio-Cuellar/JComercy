package com.jclinical.core.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PurchaseOrderCreatedEvent(
        UUID eventId,
        UUID clinicId,
        UUID purchaseOrderId,
        String folio,
        String supplierName,
        BigDecimal totalAmount,
        UUID bankAccountId,
        LocalDateTime occurredAt
) {}
