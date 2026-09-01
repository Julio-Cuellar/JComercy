package com.jclinical.inventory.infra.adapters.in.web.dto;

import com.jclinical.inventory.domain.model.MaterialReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record MaterialReservationResponse(
        UUID id,
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
