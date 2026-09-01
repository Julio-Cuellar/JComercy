package com.jclinical.inventory.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseReceiptLine {
    private UUID id;
    private UUID receiptId;
    private UUID purchaseOrderLineId;
    private UUID materialId;
    private String materialName;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private String lotNumber;
    private LocalDate expirationDate;
    private UUID inventoryMovementId;

    public BigDecimal subtotal() {
        return quantity.multiply(unitCost);
    }
}
