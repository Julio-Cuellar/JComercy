package com.jclinical.inventory.infra.adapters.in.web.dto;

import java.math.BigDecimal;

public record UpdateMaterialRequest(
        String name,
        String category,
        String internalCode,
        String brand,
        String description,
        String unitOfMeasure,
        String presentationName,
        BigDecimal quantityPerPresentation,
        BigDecimal unitCost,
        BigDecimal minimumStock,
        boolean saleEnabled,
        BigDecimal salePrice,
        boolean tracksBatches,
        boolean active
) {}
