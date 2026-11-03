package com.jcode.jcomercy.inventory.domain.model.warehouse;

import com.jcode.jcomercy.inventory.domain.exception.InventoryDomainException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WarehouseTest {

    @Test
    void requiresBranchForBranchOwnedWarehouse() {
        assertThatThrownBy(() -> warehouse(WarehouseScope.BRANCH_OWNED, null))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessage("branchId is required for a branch-owned warehouse");
    }

    @Test
    void sharedWarehouseDoesNotHavePrimaryBranch() {
        assertThatThrownBy(() -> warehouse(WarehouseScope.COMPANY_SHARED, "branch-1"))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessage("branchId must be null for a company-shared warehouse");
    }

    @Test
    void onlyActiveWarehouseAcceptsMovements() {
        assertThat(warehouse(WarehouseScope.COMPANY_SHARED, null).acceptsMovements()).isTrue();
        assertThat(new Warehouse(UUID.randomUUID(), UUID.randomUUID(), "company-1", WarehouseScope.COMPANY_SHARED,
                null, "WH-1", "Almacén", WarehouseType.GENERAL, WarehouseStatus.BLOCKED, false,
                null, null, null, null, "America/Mexico_City", "MXN", 0).acceptsMovements()).isFalse();
    }

    private static Warehouse warehouse(WarehouseScope scope, String branchId) {
        return new Warehouse(UUID.randomUUID(), UUID.randomUUID(), "company-1", scope, branchId,
                "WH-1", "Almacén", WarehouseType.GENERAL, WarehouseStatus.ACTIVE, false,
                null, null, null, null, "America/Mexico_City", "MXN", 0);
    }
}
