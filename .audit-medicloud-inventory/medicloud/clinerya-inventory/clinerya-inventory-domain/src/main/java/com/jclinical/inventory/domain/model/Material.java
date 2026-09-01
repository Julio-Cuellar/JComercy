package com.jclinical.inventory.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Material {
    private UUID id;
    private UUID clinicId;
    private String name;
    private String category;
    private String internalCode;
    private String brand;
    private String description;
    private String unitOfMeasure;
    private String presentationName;
    private BigDecimal quantityPerPresentation;
    private BigDecimal unitCost;
    private BigDecimal currentStock;
    @Builder.Default
    private BigDecimal reservedQuantity = BigDecimal.ZERO;
    private BigDecimal minimumStock;
    private boolean saleEnabled;
    private BigDecimal salePrice;
    private boolean tracksBatches;
    private boolean active;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isBelowMinimumStock() {
        return minimumStock != null && safeStock().compareTo(minimumStock) < 0;
    }

    public void increaseStock(BigDecimal quantity) {
        this.currentStock = safeStock().add(quantity);
        this.updatedAt = LocalDateTime.now();
    }

    public void receiveStock(BigDecimal quantity, BigDecimal entryUnitCost) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad recibida debe ser mayor a cero.");
        }
        if (entryUnitCost == null || entryUnitCost.signum() <= 0) {
            throw new IllegalArgumentException("El costo de recepción debe ser mayor a cero.");
        }
        BigDecimal previousStock = safeStock();
        BigDecimal nextStock = previousStock.add(quantity);
        BigDecimal previousCost = unitCost != null ? unitCost : BigDecimal.ZERO;
        this.unitCost = previousStock.multiply(previousCost)
                .add(quantity.multiply(entryUnitCost))
                .divide(nextStock, 4, RoundingMode.HALF_UP);
        this.currentStock = nextStock;
        this.updatedAt = LocalDateTime.now();
    }

    public void decreaseStock(BigDecimal quantity) {
        BigDecimal nextStock = safeStock().subtract(quantity);
        if (nextStock.signum() < 0) {
            throw new IllegalStateException("Stock insuficiente para registrar la salida de inventario.");
        }
        this.currentStock = nextStock;
        this.updatedAt = LocalDateTime.now();
    }

    public BigDecimal availableQuantity() {
        return safeStock().subtract(safeReserved());
    }

    public void reserve(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad a reservar debe ser mayor a cero.");
        }
        if (availableQuantity().compareTo(quantity) < 0) {
            throw new IllegalStateException("Stock disponible insuficiente para reservar " + quantity + " de '" + name + "'.");
        }
        this.reservedQuantity = safeReserved().add(quantity);
        this.updatedAt = LocalDateTime.now();
    }

    public void releaseReservation(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            return;
        }
        BigDecimal next = safeReserved().subtract(quantity);
        this.reservedQuantity = next.signum() < 0 ? BigDecimal.ZERO : next;
        this.updatedAt = LocalDateTime.now();
    }

    private BigDecimal safeReserved() {
        return reservedQuantity != null ? reservedQuantity : BigDecimal.ZERO;
    }

    private BigDecimal safeStock() {
        return currentStock != null ? currentStock : BigDecimal.ZERO;
    }
}
