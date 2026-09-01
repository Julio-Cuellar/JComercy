package com.jclinical.inventory.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderLine {
    private UUID id;
    private UUID purchaseOrderId;
    private UUID materialId;
    private String materialName;
    private String unitOfMeasure;
    private BigDecimal orderedQuantity;
    private BigDecimal receivedQuantity;
    private BigDecimal unitCost;
    private int lineOrder;

    public BigDecimal remainingQuantity() {
        return orderedQuantity.subtract(receivedQuantity != null ? receivedQuantity : BigDecimal.ZERO);
    }

    public BigDecimal subtotal() {
        return orderedQuantity.multiply(unitCost);
    }

    public void receive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad recibida debe ser mayor a cero.");
        }
        if (remainingQuantity().compareTo(quantity) < 0) {
            throw new IllegalStateException("La recepción excede la cantidad pendiente de " + materialName + ".");
        }
        receivedQuantity = (receivedQuantity != null ? receivedQuantity : BigDecimal.ZERO).add(quantity);
    }
}
