package com.jclinical.inventory.infra.adapters.in.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SupplierMaterialResponse(
        UUID id,
        UUID clinicId,
        UUID supplierId,
        UUID materialId,
        String materialName,
        String unitOfMeasure,
        BigDecimal supplierUnitCost,
        LocalDateTime lastSuppliedAt,
        int receiptCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
