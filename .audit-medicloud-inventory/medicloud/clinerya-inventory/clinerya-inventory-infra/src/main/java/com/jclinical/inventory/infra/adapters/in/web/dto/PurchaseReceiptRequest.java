package com.jclinical.inventory.infra.adapters.in.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PurchaseReceiptRequest(
        LocalDateTime receivedAt,
        String notes,
        List<Line> lines
) {
    public record Line(
            UUID purchaseOrderLineId,
            BigDecimal quantity,
            BigDecimal unitCost,
            String lotNumber,
            LocalDate expirationDate
    ) {}
}
