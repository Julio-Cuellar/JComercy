package com.jcode.jcomercy.inventory.infra.persistence.repository;

import com.jcode.jcomercy.inventory.infra.persistence.entity.InventoryOutboxEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InventoryOutboxEventSpringDataRepository extends JpaRepository<InventoryOutboxEventJpaEntity, UUID> {
    List<InventoryOutboxEventJpaEntity> findTop50ByStatusOrderByCreatedAtAsc(String status);
}
