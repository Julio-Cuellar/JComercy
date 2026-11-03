package com.jcode.jcomercy.inventory.infra.persistence.entity;

import com.jcode.jcomercy.inventory.domain.model.stock.ReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_reservation", schema = "inventory")
public class StockReservationJpaEntity {
    @Id private UUID id;
    @Column(name = "public_reservation_id", nullable = false, unique = true) private UUID publicReservationId;
    @Column(name = "company_id", nullable = false, length = 100) private String companyId;
    @Column(name = "branch_id", nullable = false, length = 100) private String branchId;
    @Column(name = "warehouse_id", nullable = false) private UUID warehouseId;
    @Column(name = "location_id", nullable = false) private UUID locationId;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "stock_balance_id", nullable = false) private UUID stockBalanceId;
    @Column(nullable = false, precision = 24, scale = 9) private BigDecimal quantity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ReservationStatus status;
    @Column(name = "reference_type", nullable = false, length = 100) private String referenceType;
    @Column(name = "reference_id", nullable = false, length = 150) private String referenceId;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "actor_user_id", nullable = false, length = 100) private String actorUserId;
    @Column(name = "correlation_id", nullable = false, length = 150) private String correlationId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected StockReservationJpaEntity() { }

    public StockReservationJpaEntity(UUID id, UUID publicReservationId, String companyId, String branchId,
                                     UUID warehouseId, UUID locationId, UUID productId, UUID stockBalanceId,
                                     BigDecimal quantity, ReservationStatus status, String referenceType,
                                     String referenceId, Instant expiresAt, String actorUserId, String correlationId,
                                     Instant createdAt, Instant updatedAt, long version) {
        this.id = id; this.publicReservationId = publicReservationId; this.companyId = companyId;
        this.branchId = branchId; this.warehouseId = warehouseId; this.locationId = locationId;
        this.productId = productId; this.stockBalanceId = stockBalanceId; this.quantity = quantity;
        this.status = status; this.referenceType = referenceType; this.referenceId = referenceId;
        this.expiresAt = expiresAt; this.actorUserId = actorUserId; this.correlationId = correlationId;
        this.createdAt = createdAt; this.updatedAt = updatedAt; this.version = version;
    }

    public UUID getId() { return id; }
    public UUID getPublicReservationId() { return publicReservationId; }
    public String getCompanyId() { return companyId; }
    public String getBranchId() { return branchId; }
    public UUID getWarehouseId() { return warehouseId; }
    public UUID getLocationId() { return locationId; }
    public UUID getProductId() { return productId; }
    public UUID getStockBalanceId() { return stockBalanceId; }
    public BigDecimal getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
    public String getReferenceType() { return referenceType; }
    public String getReferenceId() { return referenceId; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getActorUserId() { return actorUserId; }
    public String getCorrelationId() { return correlationId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
