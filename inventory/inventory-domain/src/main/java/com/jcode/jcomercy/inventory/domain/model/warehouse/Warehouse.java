package com.jcode.jcomercy.inventory.domain.model.warehouse;

import com.jcode.jcomercy.inventory.domain.exception.InventoryDomainException;

import java.util.Objects;
import java.util.UUID;

public record Warehouse(
        UUID id,
        UUID publicWarehouseId,
        String companyId,
        WarehouseScope scope,
        String branchId,
        String code,
        String name,
        WarehouseType type,
        WarehouseStatus status,
        boolean defaultWarehouse,
        String address,
        String phone,
        String email,
        String managerId,
        String timezone,
        String currency,
        long version
) {

    public Warehouse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(publicWarehouseId, "publicWarehouseId must not be null");
        companyId = required(companyId, "companyId");
        Objects.requireNonNull(scope, "scope must not be null");
        code = required(code, "code").toUpperCase();
        name = required(name, "name");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(status, "status must not be null");
        timezone = required(timezone, "timezone");
        currency = required(currency, "currency").toUpperCase();
        if (scope == WarehouseScope.BRANCH_OWNED && isBlank(branchId)) {
            throw new InventoryDomainException("branchId is required for a branch-owned warehouse");
        }
        if (scope == WarehouseScope.COMPANY_SHARED && !isBlank(branchId)) {
            throw new InventoryDomainException("branchId must be null for a company-shared warehouse");
        }
        if (version < 0) {
            throw new InventoryDomainException("version must not be negative");
        }
    }

    public boolean isOperational() {
        return status == WarehouseStatus.ACTIVE;
    }

    public boolean acceptsMovements() {
        return status == WarehouseStatus.ACTIVE;
    }

    public Warehouse activate() {
        if (status == WarehouseStatus.BLOCKED) {
            throw new InventoryDomainException("a blocked warehouse cannot be activated");
        }
        return new Warehouse(id, publicWarehouseId, companyId, scope, branchId, code, name, type,
                WarehouseStatus.ACTIVE, defaultWarehouse, address, phone, email, managerId, timezone, currency,
                version);
    }

    private static String required(String value, String field) {
        if (isBlank(value)) {
            throw new InventoryDomainException(field + " must not be blank");
        }
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
