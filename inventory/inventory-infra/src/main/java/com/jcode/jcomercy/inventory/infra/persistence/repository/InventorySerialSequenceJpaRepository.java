package com.jcode.jcomercy.inventory.infra.persistence.repository;

import com.jcode.jcomercy.inventory.infra.persistence.entity.InventorySerialSequenceJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventorySerialSequenceJpaRepository extends JpaRepository<InventorySerialSequenceJpaEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sequence from InventorySerialSequenceJpaEntity sequence where sequence.companyId = :companyId")
    Optional<InventorySerialSequenceJpaEntity> findForUpdate(@Param("companyId") String companyId);
}
