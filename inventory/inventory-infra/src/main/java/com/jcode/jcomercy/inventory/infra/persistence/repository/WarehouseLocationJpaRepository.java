package com.jcode.jcomercy.inventory.infra.persistence.repository;

import com.jcode.jcomercy.inventory.infra.persistence.entity.WarehouseLocationJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseLocationJpaRepository extends JpaRepository<WarehouseLocationJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select location from WarehouseLocationJpaEntity location where location.id = :id")
    Optional<WarehouseLocationJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    List<WarehouseLocationJpaEntity> findByWarehouseId(UUID warehouseId);

    List<WarehouseLocationJpaEntity> findByWarehouseIdIn(List<UUID> warehouseIds);

    boolean existsByWarehouseIdAndCode(UUID warehouseId, String code);
}
