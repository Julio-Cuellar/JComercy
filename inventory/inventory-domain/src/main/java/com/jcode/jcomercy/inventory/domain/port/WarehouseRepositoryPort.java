package com.jcode.jcomercy.inventory.domain.port;

import com.jcode.jcomercy.inventory.domain.model.warehouse.Warehouse;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface WarehouseRepositoryPort {

    Warehouse save(Warehouse warehouse);

    Optional<Warehouse> findById(UUID id);

    List<Warehouse> findByCompanyId(String companyId);

    List<Warehouse> findByBranchId(String branchId);

    List<Warehouse> findByIds(List<UUID> ids);

    boolean existsByCompanyIdAndCode(String companyId, String code);
}
