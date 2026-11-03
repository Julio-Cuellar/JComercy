package com.jcode.jcomercy.inventory.infra.persistence.repository;

import com.jcode.jcomercy.inventory.infra.persistence.entity.InventoryMovementJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InventoryMovementJpaRepository extends JpaRepository<InventoryMovementJpaEntity, UUID> {
    List<InventoryMovementJpaEntity> findByCompanyIdOrderByOccurredAtDesc(String companyId);
    List<InventoryMovementJpaEntity> findByCompanyIdAndProductIdOrderByOccurredAtDesc(String companyId, UUID productId);
}
