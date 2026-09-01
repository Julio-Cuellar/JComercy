package com.jclinical.inventory.infra.adapters.in.web.dto;

import com.jclinical.inventory.domain.model.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
        UUID id,
        UUID clinicId,
        UUID supplierId,
        String supplierName,
        String folio,
        PurchaseOrderStatus status,
        LocalDate orderDate,
        LocalDate expectedDate,
        String notes,
        UUID bankAccountId,
        BigDecimal total,
        List<Line> lines,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record Line(
            UUID id,
            UUID materialId,
            String materialName,
            String unitOfMeasure,
            BigDecimal orderedQuantity,
            BigDecimal receivedQuantity,
            BigDecimal remainingQuantity,
            BigDecimal unitCost,
            BigDecimal subtotal
    ) {}
}
