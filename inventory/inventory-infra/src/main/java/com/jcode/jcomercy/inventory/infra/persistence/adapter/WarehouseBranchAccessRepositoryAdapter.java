package com.jcode.jcomercy.inventory.infra.persistence.adapter;

import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseBranchAccess;
import com.jcode.jcomercy.inventory.domain.port.WarehouseBranchAccessRepositoryPort;
import com.jcode.jcomercy.inventory.infra.persistence.entity.WarehouseBranchAccessJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.WarehouseBranchAccessJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public class WarehouseBranchAccessRepositoryAdapter implements WarehouseBranchAccessRepositoryPort {

    private final WarehouseBranchAccessJpaRepository repository;

    public WarehouseBranchAccessRepositoryAdapter(WarehouseBranchAccessJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public WarehouseBranchAccess save(WarehouseBranchAccess access) {
        var entity = repository.save(new WarehouseBranchAccessJpaEntity(access.id(), access.warehouseId(),
                access.branchId(), access.status(), access.assignedBy()));
        return new WarehouseBranchAccess(entity.getId(), entity.getWarehouseId(), entity.getBranchId(),
                entity.getStatus(), entity.getAssignedBy());
    }

    @Override
    public boolean existsByWarehouseIdAndBranchId(UUID warehouseId, String branchId) {
        return repository.existsByWarehouseIdAndBranchIdAndStatus(warehouseId, branchId, WarehouseBranchAccess.AccessStatus.ACTIVE);
    }

    @Override
    public boolean existsActiveByWarehouseId(UUID warehouseId) {
        return repository.existsByWarehouseIdAndStatus(warehouseId, WarehouseBranchAccess.AccessStatus.ACTIVE);
    }

    @Override
    public List<UUID> findWarehouseIdsByBranchId(String branchId) {
        return repository.findByBranchIdAndStatus(branchId, WarehouseBranchAccess.AccessStatus.ACTIVE).stream()
                .map(WarehouseBranchAccessJpaEntity::getWarehouseId).toList();
    }
}
