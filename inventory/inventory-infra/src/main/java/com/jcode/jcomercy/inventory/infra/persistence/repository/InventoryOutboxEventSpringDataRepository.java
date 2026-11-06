package com.jcode.jcomercy.inventory.infra.persistence.repository;

import com.jcode.jcomercy.inventory.infra.persistence.entity.InventoryOutboxEventJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface InventoryOutboxEventSpringDataRepository extends JpaRepository<InventoryOutboxEventJpaEntity, UUID> {

    /** Los eventos listos para publicarse: pendientes que nunca fallaron o cuya espera ya venció, del más antiguo al más nuevo. */
    @Query("""
            select e from InventoryOutboxEventJpaEntity e
            where e.status = 'PENDING' and (e.nextRetryAt is null or e.nextRetryAt <= :now)
            order by e.createdAt asc
            """)
    List<InventoryOutboxEventJpaEntity> findDispatchable(@Param("now") Instant now, Pageable limit);
}
