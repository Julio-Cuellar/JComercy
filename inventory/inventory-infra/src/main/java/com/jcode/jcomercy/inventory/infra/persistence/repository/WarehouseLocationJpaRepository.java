package com.jcode.jcomercy.inventory.infra.persistence.repository;

import com.jcode.jcomercy.inventory.infra.persistence.entity.WarehouseLocationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WarehouseLocationJpaRepository extends JpaRepository<WarehouseLocationJpaEntity, UUID> {

    List<WarehouseLocationJpaEntity> findByWarehouseId(UUID warehouseId);

    List<WarehouseLocationJpaEntity> findByWarehouseIdIn(List<UUID> warehouseIds);

    boolean existsByWarehouseIdAndCode(UUID warehouseId, String code);
}
