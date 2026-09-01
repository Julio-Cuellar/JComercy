package com.jclinical.inventory.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseReceipt {
    private UUID id;
    private UUID clinicId;
    private UUID purchaseOrderId;
    private LocalDateTime receivedAt;
    private String notes;
    @Builder.Default
    private List<PurchaseReceiptLine> lines = new ArrayList<>();
    private LocalDateTime createdAt;

    public BigDecimal total() {
        return lines == null ? BigDecimal.ZERO : lines.stream()
                .map(PurchaseReceiptLine::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
