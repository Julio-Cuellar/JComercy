package com.jclinical.inventory.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrder {
    private UUID id;
    private UUID clinicId;
    private UUID supplierId;
    private String supplierName;
    private UUID bankAccountId;
    private String folio;
    private PurchaseOrderStatus status;
    private LocalDate orderDate;
    private LocalDate expectedDate;
    private String notes;
    @Builder.Default
    private List<PurchaseOrderLine> lines = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BigDecimal total() {
        return lines == null ? BigDecimal.ZERO : lines.stream()
                .map(PurchaseOrderLine::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void markOrdered() {
        if (status != PurchaseOrderStatus.DRAFT) {
            throw new IllegalStateException("Solo una orden en borrador puede enviarse al proveedor.");
        }
        status = PurchaseOrderStatus.ORDERED;
        updatedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (status == PurchaseOrderStatus.RECEIVED || status == PurchaseOrderStatus.CANCELLED) {
            throw new IllegalStateException("Esta orden ya no puede cancelarse.");
        }
        status = PurchaseOrderStatus.CANCELLED;
        updatedAt = LocalDateTime.now();
    }

    public void refreshReceptionStatus() {
        boolean allReceived = lines.stream().allMatch(line -> line.remainingQuantity().signum() == 0);
        boolean anyReceived = lines.stream().anyMatch(line -> line.getReceivedQuantity() != null
                && line.getReceivedQuantity().signum() > 0);
        status = allReceived
                ? PurchaseOrderStatus.RECEIVED
                : anyReceived ? PurchaseOrderStatus.PARTIALLY_RECEIVED : PurchaseOrderStatus.ORDERED;
        updatedAt = LocalDateTime.now();
    }
}
