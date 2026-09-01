package com.jclinical.inventory.infra.adapters.in.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record BatchResponse(
        UUID id,
        UUID materialId,
        String lotNumber,
        LocalDate expirationDate,
        BigDecimal initialQuantity,
        BigDecimal remainingQuantity,
        BigDecimal unitCostAtEntry,
        boolean depleted,
        boolean expired,
        LocalDateTime createdAt
) {}
