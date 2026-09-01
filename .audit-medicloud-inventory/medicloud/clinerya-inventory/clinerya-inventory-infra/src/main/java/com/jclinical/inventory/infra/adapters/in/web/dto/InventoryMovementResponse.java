package com.jclinical.inventory.infra.adapters.in.web.dto;

import com.jclinical.inventory.domain.model.MovementType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record InventoryMovementResponse(
        UUID id,
        UUID clinicId,
        UUID materialId,
        MovementType type,
        BigDecimal quantity,
        BigDecimal presentationQuantity,
        String presentationNameAtMovement,
        BigDecimal quantityPerPresentationAtMovement,
        BigDecimal unitCostAtMovement,
        UUID batchId,
        LocalDateTime movementDate,
        String referenceType,
        UUID referenceId,
        String notes,
        LocalDateTime createdAt
) {}
