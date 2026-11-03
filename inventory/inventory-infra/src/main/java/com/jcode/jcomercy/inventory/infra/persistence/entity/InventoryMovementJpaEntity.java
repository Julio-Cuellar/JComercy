package com.jcode.jcomercy.inventory.infra.persistence.entity;

import com.jcode.jcomercy.inventory.domain.model.stock.MovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inventory_movement", schema = "inventory")
public class InventoryMovementJpaEntity {
    @Id private UUID id;
    @Column(name = "public_movement_id", nullable = false, unique = true) private UUID publicMovementId;
    @Column(name = "company_id", nullable = false, length = 100) private String companyId;
    @Column(name = "branch_id", nullable = false, length = 100) private String branchId;
    @Column(name = "warehouse_id", nullable = false) private UUID warehouseId;
    @Column(name = "location_id", nullable = false) private UUID locationId;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private MovementType type;
    @Column(nullable = false, precision = 24, scale = 9) private BigDecimal quantity;
    @Column(name = "unit_code", nullable = false, length = 20) private String unitCode;
    @Column(name = "unit_cost", precision = 24, scale = 9) private BigDecimal unitCost;
    @Column(name = "total_cost", precision = 24, scale = 9) private BigDecimal totalCost;
    @Column(name = "lot_id", length = 100) private String lotId;
    @Column(name = "serial_number", length = 150) private String serialNumber;
    @Column(name = "expiration_date") private LocalDate expirationDate;
    @Column(name = "reference_type", length = 100) private String referenceType;
    @Column(name = "reference_id", length = 150) private String referenceId;
    @Column(length = 500) private String reason;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "actor_user_id", nullable = false, length = 100) private String actorUserId;
    @Column(name = "correlation_id", nullable = false, length = 150) private String correlationId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected InventoryMovementJpaEntity() { }
    public InventoryMovementJpaEntity(UUID id, UUID publicMovementId, String companyId, String branchId,
                                      UUID warehouseId, UUID locationId, UUID productId, MovementType type,
                                      BigDecimal quantity, String unitCode, BigDecimal unitCost, BigDecimal totalCost,
                                      String lotId, String serialNumber, LocalDate expirationDate, String referenceType,
                                      String referenceId, String reason, Instant occurredAt, String actorUserId,
                                      String correlationId, Instant createdAt) {
        this.id=id; this.publicMovementId=publicMovementId; this.companyId=companyId; this.branchId=branchId;
        this.warehouseId=warehouseId; this.locationId=locationId; this.productId=productId; this.type=type;
        this.quantity=quantity; this.unitCode=unitCode; this.unitCost=unitCost; this.totalCost=totalCost;
        this.lotId=lotId; this.serialNumber=serialNumber; this.expirationDate=expirationDate;
        this.referenceType=referenceType; this.referenceId=referenceId; this.reason=reason;
        this.occurredAt=occurredAt; this.actorUserId=actorUserId; this.correlationId=correlationId; this.createdAt=createdAt;
    }
    public UUID getId() { return id; } public UUID getPublicMovementId() { return publicMovementId; }
    public String getCompanyId() { return companyId; } public String getBranchId() { return branchId; }
    public UUID getWarehouseId() { return warehouseId; } public UUID getLocationId() { return locationId; }
    public UUID getProductId() { return productId; } public MovementType getType() { return type; }
    public BigDecimal getQuantity() { return quantity; } public String getUnitCode() { return unitCode; }
    public BigDecimal getUnitCost() { return unitCost; } public BigDecimal getTotalCost() { return totalCost; }
    public String getLotId() { return lotId; } public String getSerialNumber() { return serialNumber; }
    public LocalDate getExpirationDate() { return expirationDate; } public String getReferenceType() { return referenceType; }
    public String getReferenceId() { return referenceId; } public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; } public String getActorUserId() { return actorUserId; }
    public String getCorrelationId() { return correlationId; } public Instant getCreatedAt() { return createdAt; }
}
