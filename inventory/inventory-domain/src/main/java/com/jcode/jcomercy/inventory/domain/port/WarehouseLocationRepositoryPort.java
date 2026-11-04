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

    /**
     * Igual que {@link #findById}, pero bloquea la ubicación hasta que termine la transacción. Sirve para que dos
     * movimientos en el mismo lugar no creen a la vez el saldo de un producto que aún no lo tiene.
     */
    Optional<WarehouseLocation> findByIdForUpdate(UUID id);

    Map<UUID, List<WarehouseLocation>> findByWarehouseIds(List<UUID> warehouseIds);

    boolean existsByWarehouseIdAndCode(UUID warehouseId, String code);
}
