package com.jclinical.inventory.infra.adapters.out.persistence;

import com.jclinical.inventory.domain.model.InventoryBatch;

public interface InventoryBatchMapper {

    InventoryBatchEntity toEntity(InventoryBatch domain);

    InventoryBatch toDomain(InventoryBatchEntity entity);
}
