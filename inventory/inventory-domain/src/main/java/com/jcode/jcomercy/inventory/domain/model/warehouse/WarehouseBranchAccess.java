package com.jcode.jcomercy.inventory.domain.model.warehouse;

import com.jcode.jcomercy.inventory.domain.exception.InventoryDomainException;

import java.util.Objects;
import java.util.UUID;

public record WarehouseBranchAccess(
        UUID id,
        UUID warehouseId,
        String branchId,
        AccessStatus status,
        String assignedBy
) {

    public WarehouseBranchAccess {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(warehouseId, "warehouseId must not be null");
        branchId = required(branchId, "branchId");
        Objects.requireNonNull(status, "status must not be null");
        assignedBy = required(assignedBy, "assignedBy");
    }

    public enum AccessStatus {
        ACTIVE,
        INACTIVE
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InventoryDomainException(field + " must not be blank");
        }
        return value.trim();
    }
}
