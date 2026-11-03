package com.jcode.jcomercy.inventory.domain.model.stock;

import com.jcode.jcomercy.inventory.domain.exception.InventoryDomainException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record InventoryMovement(
        UUID id,
        UUID publicMovementId,
        String companyId,
        String branchId,
        UUID warehouseId,
        UUID locationId,
        UUID productId,
        MovementType type,
        BigDecimal quantity,
        String unitCode,
        BigDecimal unitCost,
        BigDecimal totalCost,
        String lotId,
        String serialNumber,
        LocalDate expirationDate,
        String referenceType,
        String referenceId,
        String reason,
        Instant occurredAt,
        String actorUserId,
        String correlationId,
        Instant createdAt
) {

    public InventoryMovement {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(publicMovementId, "publicMovementId must not be null");
        companyId = required(companyId, "companyId");
        branchId = required(branchId, "branchId");
        Objects.requireNonNull(warehouseId, "warehouseId must not be null");
        Objects.requireNonNull(locationId, "locationId must not be null");
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        positive(quantity, "quantity");
        unitCode = required(unitCode, "unitCode").toUpperCase();
        if (unitCost != null && unitCost.signum() < 0) {
            throw new InventoryDomainException("unitCost must not be negative");
        }
        if (totalCost != null && totalCost.signum() < 0) {
            throw new InventoryDomainException("totalCost must not be negative");
        }
        if (type == MovementType.ADJUSTMENT_IN || type == MovementType.ADJUSTMENT_OUT) {
            required(reason, "reason");
        }
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        actorUserId = required(actorUserId, "actorUserId");
        correlationId = required(correlationId, "correlationId");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
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
