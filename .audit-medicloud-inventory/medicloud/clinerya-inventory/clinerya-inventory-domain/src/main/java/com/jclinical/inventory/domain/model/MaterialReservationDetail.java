package com.jclinical.inventory.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record MaterialReservationDetail(
        UUID reservationId,
        UUID appointmentId,
        UUID patientId,
        String patientName,
        String treatmentName,
        LocalDateTime scheduledStart,
        UUID materialId,
        String materialName,
        String unitOfMeasure,
        BigDecimal quantity,
        BigDecimal currentStock,
        BigDecimal totalReservedQuantity,
        BigDecimal availableQuantity,
        MaterialReservationStatus status,
        LocalDateTime createdAt
) {
}
