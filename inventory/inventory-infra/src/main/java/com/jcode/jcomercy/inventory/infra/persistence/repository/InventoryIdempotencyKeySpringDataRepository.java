package com.jcode.jcomercy.inventory.infra.persistence.repository;

import com.jcode.jcomercy.inventory.infra.persistence.entity.InventoryIdempotencyKeyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InventoryIdempotencyKeySpringDataRepository extends JpaRepository<InventoryIdempotencyKeyJpaEntity, UUID> {
    Optional<InventoryIdempotencyKeyJpaEntity> findByCompanyIdAndActorUserIdAndIdempotencyKey(
            String companyId, String actorUserId, String idempotencyKey
    );
}
