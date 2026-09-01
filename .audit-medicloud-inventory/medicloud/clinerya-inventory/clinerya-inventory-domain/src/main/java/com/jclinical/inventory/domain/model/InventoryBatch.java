package com.jclinical.inventory.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryBatch {
    private UUID id;
    private UUID clinicId;
    private UUID materialId;
    private String lotNumber;
    private LocalDate expirationDate;
    private BigDecimal initialQuantity;
    private BigDecimal remainingQuantity;
    private BigDecimal unitCostAtEntry;
    private LocalDateTime createdAt;

    public void decrease(BigDecimal quantity) {
        BigDecimal next = remainingQuantity.subtract(quantity);
        if (next.signum() < 0) {
            throw new IllegalStateException("El lote no tiene existencia suficiente para esta salida.");
        }
        remainingQuantity = next;
    }

    public void increase(BigDecimal quantity) {
        remainingQuantity = remainingQuantity.add(quantity);
    }

    public boolean isDepleted() {
        return remainingQuantity.signum() <= 0;
    }

    public boolean isExpired() {
        return expirationDate != null && expirationDate.isBefore(java.time.LocalDate.now());
    }
}
