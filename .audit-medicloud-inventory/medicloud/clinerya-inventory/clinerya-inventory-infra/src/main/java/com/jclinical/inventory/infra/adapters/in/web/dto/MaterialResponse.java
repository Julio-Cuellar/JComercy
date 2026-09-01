package com.jclinical.inventory.infra.adapters.in.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record MaterialResponse(
        UUID id,
        UUID clinicId,
        String name,
        String category,
        String internalCode,
        String brand,
        String description,
        String unitOfMeasure,
        String presentationName,
        BigDecimal quantityPerPresentation,
        BigDecimal unitCost,
        BigDecimal currentStock,
        BigDecimal reservedQuantity,
        BigDecimal availableQuantity,
        BigDecimal minimumStock,
        boolean belowMinimumStock,
        boolean saleEnabled,
        BigDecimal salePrice,
        boolean tracksBatches,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
