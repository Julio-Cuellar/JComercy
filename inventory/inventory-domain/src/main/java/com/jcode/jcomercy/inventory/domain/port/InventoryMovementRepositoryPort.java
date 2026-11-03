package com.jcode.jcomercy.inventory.domain.port;

import com.jcode.jcomercy.inventory.domain.model.stock.InventoryMovement;

import java.util.List;
import java.util.UUID;

public interface InventoryMovementRepositoryPort {
    InventoryMovement save(InventoryMovement movement);
    List<InventoryMovement> findByCompanyId(String companyId);
    List<InventoryMovement> findByCompanyIdAndProductId(String companyId, UUID productId);
}
