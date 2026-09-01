package com.jcode.jcomercy.inventory.domain.model.warehouse;

import com.jcode.jcomercy.inventory.domain.exception.InventoryDomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WarehouseLocationTest {

    @Test
    void blockedLocationDoesNotAcceptStock() {
        var location = location(LocationStatus.BLOCKED, null);

        assertThat(location.acceptsStock()).isFalse();
    }

    @Test
    void capacityRequiresItsUnit() {
        assertThatThrownBy(() -> new WarehouseLocation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                "A-01", "Ubicación", LocationType.INTERNAL, LocationStatus.ACTIVE,
                new BigDecimal("10"), null))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessage("capacityUnitCode is required when capacity is provided");
    }

    private static WarehouseLocation location(LocationStatus status, BigDecimal capacity) {
        return new WarehouseLocation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                "A-01", "Ubicación", LocationType.INTERNAL, status, capacity,
                capacity == null ? null : "PZA");
    }
}
