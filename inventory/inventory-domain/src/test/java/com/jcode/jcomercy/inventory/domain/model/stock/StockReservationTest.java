package com.jcode.jcomercy.inventory.domain.model.stock;

import com.jcode.jcomercy.inventory.domain.exception.InventoryDomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockReservationTest {

    private static final Instant CREATED = Instant.parse("2026-11-02T10:00:00Z");
    private static final Instant EXPIRES = Instant.parse("2026-11-02T10:30:00Z");

    @Test
    void committingAnActiveReservationMarksItCommitted() {
        var now = Instant.parse("2026-11-02T10:10:00Z");

        var committed = reservation(EXPIRES).commit(now);

        assertThat(committed.status()).isEqualTo(ReservationStatus.COMMITTED);
        assertThat(committed.updatedAt()).isEqualTo(now);
        assertThat(committed.version()).isEqualTo(1);
    }

    @Test
    void releasingAnActiveReservationMarksItReleased() {
        var released = reservation(null).release(Instant.parse("2026-11-02T10:05:00Z"));

        assertThat(released.status()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void aReservationLeavesTheActiveStateOnlyOnce() {
        var released = reservation(EXPIRES).release(CREATED.plusSeconds(60));

        assertThatThrownBy(() -> released.commit(CREATED.plusSeconds(120)))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessage("reservation is not active");
        assertThatThrownBy(() -> released.release(CREATED.plusSeconds(120)))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessage("reservation is not active");
    }

    @Test
    void expiresOnlyOnceItsDeadlineHasPassed() {
        var active = reservation(EXPIRES);

        assertThat(active.isDue(EXPIRES.minusSeconds(1))).isFalse();
        assertThat(active.isDue(EXPIRES)).isTrue();
        assertThatThrownBy(() -> active.expire(EXPIRES.minusSeconds(1)))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessage("reservation has not expired yet");
        assertThat(active.expire(EXPIRES).status()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void aReservationWithoutDeadlineNeverExpires() {
        assertThat(reservation(null).isDue(Instant.parse("2099-01-01T00:00:00Z"))).isFalse();
    }

    @Test
    void referenceIsNormalizedSoLookupsAreCaseInsensitive() {
        var reservation = new StockReservation(UUID.randomUUID(), UUID.randomUUID(), "company-1", "branch-1",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("2"),
                ReservationStatus.ACTIVE, " order_line ", " ml-123-1 ", null, "user-1", "cor-1", CREATED, CREATED, 0);

        assertThat(reservation.referenceType()).isEqualTo("ORDER_LINE");
        assertThat(reservation.referenceId()).isEqualTo("ML-123-1");
        assertThat(StockReservation.normalizeReference(" ml-123-1 ")).isEqualTo("ML-123-1");
    }

    @Test
    void rejectsNonPositiveQuantityAndMissingReference() {
        assertThatThrownBy(() -> new StockReservation(UUID.randomUUID(), UUID.randomUUID(), "company-1", "branch-1",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO,
                ReservationStatus.ACTIVE, "ORDER_LINE", "REF-1", null, "user-1", "cor-1", CREATED, CREATED, 0))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessage("quantity must be greater than zero");
        assertThatThrownBy(() -> new StockReservation(UUID.randomUUID(), UUID.randomUUID(), "company-1", "branch-1",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE,
                ReservationStatus.ACTIVE, "ORDER_LINE", " ", null, "user-1", "cor-1", CREATED, CREATED, 0))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessage("referenceId must not be blank");
    }

    @Test
    void deadlineMustBeAfterCreation() {
        assertThatThrownBy(() -> reservation(CREATED))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessage("expiresAt must be after createdAt");
    }

    private static StockReservation reservation(Instant expiresAt) {
        return new StockReservation(UUID.randomUUID(), UUID.randomUUID(), "company-1", "branch-1",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("2"),
                ReservationStatus.ACTIVE, "ORDER_LINE", "ML-123-1", expiresAt, "user-1", "cor-1", CREATED, CREATED, 0);
    }
}
