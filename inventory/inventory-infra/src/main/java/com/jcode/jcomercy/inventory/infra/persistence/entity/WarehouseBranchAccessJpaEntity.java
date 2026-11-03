package com.jcode.jcomercy.inventory.infra.persistence.entity;

import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseBranchAccess.AccessStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "warehouse_branch_access", schema = "inventory")
public class WarehouseBranchAccessJpaEntity {

    @Id
    private UUID id;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "branch_id", nullable = false, length = 100)
    private String branchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccessStatus status;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "assigned_by", nullable = false, length = 100)
    private String assignedBy;

    protected WarehouseBranchAccessJpaEntity() {
    }

    public WarehouseBranchAccessJpaEntity(UUID id, UUID warehouseId, String branchId, AccessStatus status, String assignedBy) {
        this.id = id;
        this.warehouseId = warehouseId;
        this.branchId = branchId;
        this.status = status;
        this.assignedBy = assignedBy;
        this.assignedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getWarehouseId() { return warehouseId; }
    public String getBranchId() { return branchId; }
    public AccessStatus getStatus() { return status; }
    public String getAssignedBy() { return assignedBy; }
    public Instant getAssignedAt() { return assignedAt; }
}
