package com.jcode.jcomercy.inventory.domain.port;

import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseLocation;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Optional;

public interface WarehouseLocationRepositoryPort {

    WarehouseLocation save(WarehouseLocation location);

    List<WarehouseLocation> findByWarehouseId(UUID warehouseId);

    Optional<WarehouseLocation> findById(UUID id);

    Map<UUID, List<WarehouseLocation>> findByWarehouseIds(List<UUID> warehouseIds);

    boolean existsByWarehouseIdAndCode(UUID warehouseId, String code);
}
