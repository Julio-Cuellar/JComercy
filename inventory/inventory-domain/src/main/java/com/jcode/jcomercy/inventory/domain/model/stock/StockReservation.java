package com.jcode.jcomercy.inventory.domain.model.stock;

import com.jcode.jcomercy.inventory.domain.exception.InventoryDomainException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Stock set aside against a single balance for a business reference (for example an order line).
 * It leaves the ACTIVE state exactly once: committed (shipped), released (cancelled) or expired.
 */
public record StockReservation(
        UUID id,
        UUID publicReservationId,
        String companyId,
        String branchId,
        UUID warehouseId,
        UUID locationId,
        UUID productId,
        UUID stockBalanceId,
        BigDecimal quantity,
        ReservationStatus status,
        String referenceType,
        String referenceId,
        Instant expiresAt,
        String actorUserId,
        String correlationId,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    public StockReservation {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(publicReservationId, "publicReservationId must not be null");
        companyId = required(companyId, "companyId");
        branchId = required(branchId, "branchId");
        Objects.requireNonNull(warehouseId, "warehouseId must not be null");
        Objects.requireNonNull(locationId, "locationId must not be null");
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(stockBalanceId, "stockBalanceId must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        if (quantity.signum() <= 0) {
            throw new InventoryDomainException("quantity must be greater than zero");
        }
        Objects.requireNonNull(status, "status must not be null");
        referenceType = required(normalizeReference(referenceType), "referenceType");
        referenceId = required(normalizeReference(referenceId), "referenceId");
        actorUserId = required(actorUserId, "actorUserId");
        correlationId = required(correlationId, "correlationId");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (expiresAt != null && !expiresAt.isAfter(createdAt)) {
            throw new InventoryDomainException("expiresAt must be after createdAt");
        }
        if (version < 0) {
            throw new InventoryDomainException("version must not be negative");
        }
    }

    /** References are matched case-insensitively, so both sides of a lookup use this form. */
    public static String normalizeReference(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    public boolean isDue(Instant now) {
        return status == ReservationStatus.ACTIVE && expiresAt != null && !now.isBefore(expiresAt);
    }

    public StockReservation commit(Instant now) {
        return transition(ReservationStatus.COMMITTED, now);
    }

    public StockReservation release(Instant now) {
        return transition(ReservationStatus.RELEASED, now);
    }

    public StockReservation expire(Instant now) {
        requireActive();
        if (!isDue(now)) {
            throw new InventoryDomainException("reservation has not expired yet");
        }
        return transition(ReservationStatus.EXPIRED, now);
    }

    private StockReservation transition(ReservationStatus target, Instant now) {
        requireActive();
        Objects.requireNonNull(now, "now must not be null");
        return new StockReservation(id, publicReservationId, companyId, branchId, warehouseId, locationId, productId,
                stockBalanceId, quantity, target, referenceType, referenceId, expiresAt, actorUserId, correlationId,
                createdAt, now, version + 1);
    }

    private void requireActive() {
        if (status != ReservationStatus.ACTIVE) {
            throw new InventoryDomainException("reservation is not active");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InventoryDomainException(field + " must not be blank");
        }
        return value.trim();
    }
}
