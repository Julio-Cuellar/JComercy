package com.jclinical.inventory.infra.adapters.in.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderRequest(
        UUID supplierId,
        LocalDate orderDate,
        LocalDate expectedDate,
        String notes,
        UUID bankAccountId,
        List<Line> lines
) {
    public record Line(UUID materialId, BigDecimal quantity, BigDecimal unitCost) {}
}
