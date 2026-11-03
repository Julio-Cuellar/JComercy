package com.jcode.jcomercy.inventory.infra.persistence.repository;

import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseBranchAccess.AccessStatus;
import com.jcode.jcomercy.inventory.infra.persistence.entity.WarehouseBranchAccessJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WarehouseBranchAccessJpaRepository extends JpaRepository<WarehouseBranchAccessJpaEntity, UUID> {

    boolean existsByWarehouseIdAndBranchIdAndStatus(UUID warehouseId, String branchId, AccessStatus status);

    boolean existsByWarehouseIdAndStatus(UUID warehouseId, AccessStatus status);

    List<WarehouseBranchAccessJpaEntity> findByBranchIdAndStatus(String branchId, AccessStatus status);
}
