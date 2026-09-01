package com.jcode.jcomercy.inventory.infra.persistence.entity;

import com.jcode.jcomercy.inventory.domain.model.stock.InventoryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "stock_balance", schema = "inventory")
public class StockBalanceJpaEntity {
    @Id private UUID id;
    @Column(name = "company_id", nullable = false, length = 100) private String companyId;
    @Column(name = "branch_id", nullable = false, length = 100) private String branchId;
    @Column(name = "warehouse_id", nullable = false) private UUID warehouseId;
    @Column(name = "location_id", nullable = false) private UUID locationId;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "lot_id", length = 100) private String lotId;
    @Column(name = "serial_number", length = 150) private String serialNumber;
    @Column(name = "expiration_date") private LocalDate expirationDate;
    @Enumerated(EnumType.STRING) @Column(name = "inventory_status", nullable = false, length = 20)
    private InventoryStatus inventoryStatus;
    @Column(name = "on_hand", nullable = false, precision = 24, scale = 9) private BigDecimal onHand;
    @Column(nullable = false, precision = 24, scale = 9) private BigDecimal reserved;
    @Column(name = "unit_cost", precision = 24, scale = 9) private BigDecimal unitCost;
    @Column(name = "costing_method", length = 40) private String costingMethod;
    @Version @Column(nullable = false) private long version;

    protected StockBalanceJpaEntity() { }

    public StockBalanceJpaEntity(UUID id, String companyId, String branchId, UUID warehouseId, UUID locationId,
                                 UUID productId, String lotId, String serialNumber, LocalDate expirationDate,
                                 InventoryStatus inventoryStatus, BigDecimal onHand, BigDecimal reserved,
                                 BigDecimal unitCost, String costingMethod, long version) {
        this.id = id; this.companyId = companyId; this.branchId = branchId; this.warehouseId = warehouseId;
        this.locationId = locationId; this.productId = productId; this.lotId = lotId; this.serialNumber = serialNumber;
        this.expirationDate = expirationDate; this.inventoryStatus = inventoryStatus; this.onHand = onHand;
        this.reserved = reserved; this.unitCost = unitCost; this.costingMethod = costingMethod; this.version = version;
    }
    public UUID getId() { return id; }
    public String getCompanyId() { return companyId; }
    public String getBranchId() { return branchId; }
    public UUID getWarehouseId() { return warehouseId; }
    public UUID getLocationId() { return locationId; }
    public UUID getProductId() { return productId; }
    public String getLotId() { return lotId; }
    public String getSerialNumber() { return serialNumber; }
    public LocalDate getExpirationDate() { return expirationDate; }
    public InventoryStatus getInventoryStatus() { return inventoryStatus; }
    public BigDecimal getOnHand() { return onHand; }
    public BigDecimal getReserved() { return reserved; }
    public BigDecimal getUnitCost() { return unitCost; }
    public String getCostingMethod() { return costingMethod; }
    public long getVersion() { return version; }
}
