package com.jclinical.core.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record MaterialReservationRequestedEvent(
        UUID eventId,
        UUID clinicId,
        UUID appointmentId,
        UUID quotationId,
        UUID quotationItemId,
        LocalDateTime requestedAt,
        List<ReservationLine> materials
) {

    public record ReservationLine(
            UUID materialId,
            String materialName,
            BigDecimal quantity
    ) {}
}
