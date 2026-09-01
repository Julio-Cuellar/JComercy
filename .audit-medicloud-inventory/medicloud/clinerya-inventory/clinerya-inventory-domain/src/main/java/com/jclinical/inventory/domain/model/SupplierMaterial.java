package com.jclinical.inventory.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierMaterial {
    private UUID id;
    private UUID clinicId;
    private UUID supplierId;
    private UUID materialId;
    private String materialName;
    private String unitOfMeasure;
    private BigDecimal supplierUnitCost;
    private LocalDateTime lastSuppliedAt;
    private int receiptCount;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void activate(BigDecimal unitCost) {
        active = true;
        if (unitCost != null && unitCost.signum() > 0) {
            supplierUnitCost = unitCost;
        }
        updatedAt = LocalDateTime.now();
    }

    public void deactivate() {
        active = false;
        updatedAt = LocalDateTime.now();
    }

    public void markSupplied(BigDecimal unitCost, LocalDateTime suppliedAt) {
        supplierUnitCost = unitCost;
        lastSuppliedAt = suppliedAt;
        receiptCount++;
        active = true;
        updatedAt = LocalDateTime.now();
    }
}
