package com.jclinical.inventory.infra.adapters.in.web.dto;

import com.jclinical.inventory.domain.model.MovementType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record RegisterInventoryMovementRequest(
        MovementType type,
        BigDecimal quantity,
        BigDecimal presentationQuantity,
        LocalDateTime movementDate,
        String notes,
        String lotNumber,
        LocalDate expirationDate,
        UUID batchId
) {}
