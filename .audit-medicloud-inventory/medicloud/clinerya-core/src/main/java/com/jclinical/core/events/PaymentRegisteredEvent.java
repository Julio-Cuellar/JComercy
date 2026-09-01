package com.jclinical.core.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Momento 1 del ARE: cobro registrado en Caja.
 *
 * cashAmount conserva el total agregado en efectivo. nonCashAmount se conserva
 * como total agregado y fallback; nonCashLines permite asociar transferencias,
 * tarjetas y cheques con una cuenta bancaria concreta para conciliacion.
 */
public record PaymentRegisteredEvent(
        UUID eventId,
        UUID clinicId,
        UUID patientId,
        UUID ticketId,
        UUID cashSessionId,
        UUID quotationId,
        BigDecimal cashAmount,
        BigDecimal nonCashAmount,
        List<NonCashPaymentLine> nonCashLines,
        boolean fullyPaid,
        LocalDateTime occurredAt
) {
    public record NonCashPaymentLine(
            UUID bankAccountId,
            BigDecimal amount,
            String method,
            String reference
    ) {}
}
