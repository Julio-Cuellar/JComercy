package com.jclinical.inventory.infra.adapters.in.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SupplierMaterialRequest(
        UUID materialId,
        BigDecimal supplierUnitCost
) {}
