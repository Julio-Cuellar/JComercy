package com.jcode.jcomercy.inventory.domain.port;

import com.jcode.jcomercy.inventory.domain.model.stock.InventoryStatus;
import com.jcode.jcomercy.inventory.domain.model.stock.StockBalance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockBalanceRepositoryPort {
    StockBalance save(StockBalance balance);
    Optional<StockBalance> findForUpdate(UUID warehouseId, UUID locationId, UUID productId, String lotId,
                                         String serialNumber, InventoryStatus inventoryStatus);
    /** Locks every balance of the product in the warehouse, ordered by id so concurrent callers lock in the same order. */
    List<StockBalance> findAllocatableForUpdate(String companyId, UUID warehouseId, UUID productId,
                                                InventoryStatus inventoryStatus, UUID locationId);
    Optional<StockBalance> findByIdForUpdate(UUID id);
    boolean existsByCompanyIdAndSerialNumber(String companyId, String serialNumber);
    List<StockBalance> findByCompanyId(String companyId);
    List<StockBalance> findByCompanyIdAndProductId(String companyId, UUID productId);
}
