package com.jcode.jcomercy.inventory.domain.model.warehouse;

import com.jcode.jcomercy.inventory.domain.exception.InventoryDomainException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record WarehouseLocation(
        UUID id,
        UUID publicLocationId,
        UUID warehouseId,
        UUID parentLocationId,
        String code,
        String name,
        LocationType type,
        LocationStatus status,
        BigDecimal capacity,
        String capacityUnitCode
) {

    public WarehouseLocation {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(publicLocationId, "publicLocationId must not be null");
        Objects.requireNonNull(warehouseId, "warehouseId must not be null");
        code = required(code, "code").toUpperCase();
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(status, "status must not be null");
        capacityUnitCode = optionalUpper(capacityUnitCode);
        if (capacity != null && capacity.signum() <= 0) {
            throw new InventoryDomainException("capacity must be greater than zero");
        }
        if (capacity != null && capacityUnitCode == null) {
            throw new InventoryDomainException("capacityUnitCode is required when capacity is provided");
        }
        if (parentLocationId != null && parentLocationId.equals(id)) {
            throw new InventoryDomainException("a location cannot be its own parent");
        }
    }

    public boolean acceptsStock() {
        return status == LocationStatus.ACTIVE;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InventoryDomainException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalUpper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }
}
