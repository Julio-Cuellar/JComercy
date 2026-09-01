package com.jcode.jcomercy.inventory.infra.persistence.entity;

import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseScope;
import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseStatus;
import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "warehouse", schema = "inventory")
public class WarehouseJpaEntity {

    @Id
    private UUID id;

    @Column(name = "public_warehouse_id", nullable = false, unique = true)
    private UUID publicWarehouseId;

    @Column(name = "company_id", nullable = false, length = 100)
    private String companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WarehouseScope scope;

    @Column(name = "branch_id", length = 100)
    private String branchId;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WarehouseType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WarehouseStatus status;

    @Column(name = "is_default", nullable = false)
    private boolean defaultWarehouse;

    @Column(length = 1000)
    private String address;

    @Column(length = 50)
    private String phone;

    @Column(length = 254)
    private String email;

    @Column(name = "manager_id", length = 100)
    private String managerId;

    @Column(nullable = false, length = 100)
    private String timezone;

    @Column(nullable = false, length = 10)
    private String currency;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WarehouseJpaEntity() {
    }

    public WarehouseJpaEntity(UUID id, UUID publicWarehouseId, String companyId, WarehouseScope scope,
                              String branchId, String code, String name, WarehouseType type,
                              WarehouseStatus status, boolean defaultWarehouse, String address,
                              String phone, String email, String managerId, String timezone, String currency,
                              long version) {
        this.id = id;
        this.publicWarehouseId = publicWarehouseId;
        this.companyId = companyId;
        this.scope = scope;
        this.branchId = branchId;
        this.code = code;
        this.name = name;
        this.type = type;
        this.status = status;
        this.defaultWarehouse = defaultWarehouse;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.managerId = managerId;
        this.timezone = timezone;
        this.currency = currency;
        this.version = version;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getPublicWarehouseId() { return publicWarehouseId; }
    public String getCompanyId() { return companyId; }
    public WarehouseScope getScope() { return scope; }
    public String getBranchId() { return branchId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public WarehouseType getType() { return type; }
    public WarehouseStatus getStatus() { return status; }
    public boolean isDefaultWarehouse() { return defaultWarehouse; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getManagerId() { return managerId; }
    public String getTimezone() { return timezone; }
    public String getCurrency() { return currency; }
    public long getVersion() { return version; }
}
