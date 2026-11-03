package com.jcode.jcomercy.inventory.infra.persistence.entity;

import com.jcode.jcomercy.inventory.domain.model.warehouse.LocationStatus;
import com.jcode.jcomercy.inventory.domain.model.warehouse.LocationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "warehouse_location", schema = "inventory")
public class WarehouseLocationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "public_location_id", nullable = false, unique = true)
    private UUID publicLocationId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "parent_location_id")
    private UUID parentLocationId;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LocationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LocationStatus status;

    @Column(precision = 24, scale = 9)
    private BigDecimal capacity;

    @Column(name = "capacity_unit_code", length = 20)
    private String capacityUnitCode;

    protected WarehouseLocationJpaEntity() {
    }

    public WarehouseLocationJpaEntity(UUID id, UUID publicLocationId, UUID warehouseId, UUID parentLocationId,
                                      String code, String name, LocationType type, LocationStatus status,
                                      BigDecimal capacity, String capacityUnitCode) {
        this.id = id;
        this.publicLocationId = publicLocationId;
        this.warehouseId = warehouseId;
        this.parentLocationId = parentLocationId;
        this.code = code;
        this.name = name;
        this.type = type;
        this.status = status;
        this.capacity = capacity;
        this.capacityUnitCode = capacityUnitCode;
    }

    public UUID getId() { return id; }
    public UUID getPublicLocationId() { return publicLocationId; }
    public UUID getWarehouseId() { return warehouseId; }
    public UUID getParentLocationId() { return parentLocationId; }
    public String getCode() { return code; }
    public LocationStatus getStatus() { return status; }
    public String getName() { return name; }
    public LocationType getType() { return type; }
    public BigDecimal getCapacity() { return capacity; }
    public String getCapacityUnitCode() { return capacityUnitCode; }
}
