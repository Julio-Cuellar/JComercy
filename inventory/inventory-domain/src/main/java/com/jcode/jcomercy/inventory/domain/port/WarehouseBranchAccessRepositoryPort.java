package com.jcode.jcomercy.inventory.domain.port;

import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseBranchAccess;

import java.util.UUID;
import java.util.List;

public interface WarehouseBranchAccessRepositoryPort {

    WarehouseBranchAccess save(WarehouseBranchAccess access);

    boolean existsByWarehouseIdAndBranchId(UUID warehouseId, String branchId);

    boolean existsActiveByWarehouseId(UUID warehouseId);

    List<UUID> findWarehouseIdsByBranchId(String branchId);
}
