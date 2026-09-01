package com.jcode.jcomercy.inventory.domain.model.stock;

import com.jcode.jcomercy.inventory.domain.exception.InventoryDomainException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record StockBalance(
        UUID id,
        String companyId,
        String branchId,
        UUID warehouseId,
        UUID locationId,
        UUID productId,
        String lotId,
        String serialNumber,
        java.time.LocalDate expirationDate,
        InventoryStatus inventoryStatus,
        BigDecimal onHand,
        BigDecimal reserved,
        BigDecimal unitCost,
        String costingMethod,
        long version
) {

    public StockBalance {
        Objects.requireNonNull(id, "id must not be null");
        companyId = required(companyId, "companyId");
        branchId = required(branchId, "branchId");
        Objects.requireNonNull(warehouseId, "warehouseId must not be null");
        Objects.requireNonNull(locationId, "locationId must not be null");
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(inventoryStatus, "inventoryStatus must not be null");
        Objects.requireNonNull(onHand, "onHand must not be null");
        reserved = nonNegative(reserved, "reserved");
        if (onHand.signum() < 0 && reserved.signum() > 0) {
            throw new InventoryDomainException("negative stock cannot have reservations");
        }
        if (onHand.signum() >= 0 && reserved.compareTo(onHand) > 0) {
            throw new InventoryDomainException("reserved cannot be greater than onHand");
        }
        if (unitCost != null && unitCost.signum() < 0) {
            throw new InventoryDomainException("unitCost must not be negative");
        }
        if (serialNumber != null && !serialNumber.isBlank() && onHand.compareTo(BigDecimal.ONE) > 0) {
            throw new InventoryDomainException("a serialized balance cannot contain more than one unit");
        }
        if (version < 0) {
            throw new InventoryDomainException("version must not be negative");
        }
    }

    public BigDecimal available() {
        return onHand.subtract(reserved);
    }

    public StockBalance receive(BigDecimal quantity, BigDecimal newUnitCost) {
        positive(quantity, "quantity");
        if (newUnitCost != null && newUnitCost.signum() < 0) {
            throw new InventoryDomainException("unitCost must not be negative");
        }
        return withQuantities(onHand.add(quantity), reserved, newUnitCost == null ? unitCost : newUnitCost);
    }

    public StockBalance issue(BigDecimal quantity, boolean allowNegativeStock) {
        positive(quantity, "quantity");
        if (!allowNegativeStock && available().compareTo(quantity) < 0) {
            throw new InventoryDomainException("insufficient available stock");
        }
        return withQuantities(onHand.subtract(quantity), reserved, unitCost);
    }

    public StockBalance reserve(BigDecimal quantity) {
        positive(quantity, "quantity");
        if (available().compareTo(quantity) < 0) {
            throw new InventoryDomainException("insufficient available stock for reservation");
        }
        return withQuantities(onHand, reserved.add(quantity), unitCost);
    }

    public StockBalance releaseReservation(BigDecimal quantity) {
        positive(quantity, "quantity");
        if (reserved.compareTo(quantity) < 0) {
            throw new InventoryDomainException("release quantity exceeds reserved stock");
        }
        return withQuantities(onHand, reserved.subtract(quantity), unitCost);
    }

    private StockBalance withQuantities(BigDecimal newOnHand, BigDecimal newReserved, BigDecimal newUnitCost) {
        return new StockBalance(id, companyId, branchId, warehouseId, locationId, productId, lotId,
                serialNumber, expirationDate, inventoryStatus, newOnHand, newReserved, newUnitCost,
                costingMethod, version + 1);
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.signum() < 0) {
            throw new InventoryDomainException(field + " must not be negative");
        }
        return value;
    }

    private static void positive(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.signum() <= 0) {
            throw new InventoryDomainException(field + " must be greater than zero");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InventoryDomainException(field + " must not be blank");
        }
        return value.trim();
    }
}
