package com.jclinical.inventory.infra.adapters.in.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PurchaseReceiptResponse(
        UUID id,
        UUID clinicId,
        UUID purchaseOrderId,
        LocalDateTime receivedAt,
        String notes,
        BigDecimal total,
        List<Line> lines,
        LocalDateTime createdAt
) {
    public record Line(
            UUID id,
            UUID purchaseOrderLineId,
            UUID materialId,
            String materialName,
            BigDecimal quantity,
            BigDecimal unitCost,
            String lotNumber,
            LocalDate expirationDate,
            UUID inventoryMovementId,
            BigDecimal subtotal
    ) {}
}
