package com.jclinical.inventory.infra.adapters.out.persistence;

import com.jclinical.inventory.domain.model.InventoryMovement;

public interface InventoryMovementMapper {

    InventoryMovementEntity toEntity(InventoryMovement domain);

    InventoryMovement toDomain(InventoryMovementEntity entity);
}
