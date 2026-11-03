package com.jcode.jcomercy.inventory.domain.model.stock;

import com.jcode.jcomercy.inventory.domain.exception.InventoryDomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryMovementTest {

    @Test
    void adjustmentRequiresReason() {
        assertThatThrownBy(() -> movement(MovementType.ADJUSTMENT_IN, null))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessage("reason must not be blank");
    }

    @Test
    void movementQuantityMustBePositive() {
        assertThatThrownBy(() -> movement(MovementType.RECEIPT, BigDecimal.ZERO))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessage("quantity must be greater than zero");
    }

    private static InventoryMovement movement(MovementType type, BigDecimal quantity) {
        return new InventoryMovement(UUID.randomUUID(), UUID.randomUUID(), "company-1", "branch-1", UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), type, quantity == null ? BigDecimal.ONE : quantity, "PZA",
                null, null, null, null, null, null, null, null, Instant.now(), "user-1", "corr-1", Instant.now());
    }
}
