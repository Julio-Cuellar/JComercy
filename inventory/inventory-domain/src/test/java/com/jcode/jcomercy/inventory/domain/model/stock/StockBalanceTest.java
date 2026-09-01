package com.jcode.jcomercy.inventory.domain.model.stock;

import com.jcode.jcomercy.inventory.domain.exception.InventoryDomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockBalanceTest {

    @Test
    void receiveAndIssueUpdateAvailableStockAndVersion() {
        var balance = balance("10", "2");

        var received = balance.receive(new BigDecimal("5"), new BigDecimal("12.50"));
        var issued = received.issue(new BigDecimal("4"), false);

        assertThat(issued.onHand()).isEqualByComparingTo("11");
        assertThat(issued.reserved()).isEqualByComparingTo("2");
        assertThat(issued.available()).isEqualByComparingTo("9");
        assertThat(issued.version()).isEqualTo(2);
        assertThat(issued.unitCost()).isEqualByComparingTo("12.50");
    }

    @Test
    void rejectsIssueAboveAvailableStockUnlessNegativeStockIsAllowed() {
        var balance = balance("10", "2");

        assertThatThrownBy(() -> balance.issue(new BigDecimal("9"), false))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessage("insufficient available stock");

        assertThat(balance("10", "0").issue(new BigDecimal("12"), true).onHand()).isEqualByComparingTo("-2");
    }

    @Test
    void reservationCannotExceedAvailableStock() {
        var balance = balance("10", "2");

        assertThatThrownBy(() -> balance.reserve(new BigDecimal("9")))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessage("insufficient available stock for reservation");
    }

    @Test
    void serializedBalanceCanContainOnlyOneUnit() {
        assertThatThrownBy(() -> new StockBalance(UUID.randomUUID(), "company-1", "branch-1", UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), null, "SER-1", null, InventoryStatus.AVAILABLE,
                new BigDecimal("2"), BigDecimal.ZERO, null, null, 0))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessage("a serialized balance cannot contain more than one unit");
    }

    private static StockBalance balance(String onHand, String reserved) {
        return new StockBalance(UUID.randomUUID(), "company-1", "branch-1", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, null, null, InventoryStatus.AVAILABLE, new BigDecimal(onHand),
                new BigDecimal(reserved), new BigDecimal("10.00"), "WEIGHTED_AVERAGE_MOVING", 0);
    }
}
