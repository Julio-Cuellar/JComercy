package com.jclinical.inventory.infra.adapters.in.web.dto;

import com.jclinical.inventory.domain.model.MovementType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record GeneralLedgerEntryResponse(
        UUID id,
        UUID materialId,
        String materialName,
        String materialUnitOfMeasure,
        MovementType type,
        BigDecimal quantity,
        BigDecimal unitCostAtMovement,
        UUID batchId,
        LocalDateTime movementDate,
        String referenceType,
        UUID referenceId,
        String notes,
        LocalDateTime createdAt
) {}
