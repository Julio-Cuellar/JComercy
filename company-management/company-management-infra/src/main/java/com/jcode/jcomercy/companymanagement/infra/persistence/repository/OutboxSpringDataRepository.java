package com.jcode.jcomercy.companymanagement.infra.persistence.repository;

import com.jcode.jcomercy.companymanagement.infra.persistence.entity.OutboxEventJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxSpringDataRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {
    boolean existsByEventId(String eventId);

    /**
     * Los eventos listos para publicarse: pendientes que nunca fallaron o cuya espera ya venció, del más antiguo al más
     * nuevo. Una sola consulta: con dos (primero los nuevos y luego los reintentos) un evento nuevo salía antes que uno
     * más antiguo que esperaba su reintento (BIT-038).
     */
    @Query("""
            select e from OutboxEventJpaEntity e
            where e.status = 'PENDING' and (e.nextRetryAt is null or e.nextRetryAt <= :now)
            order by e.createdAt asc
            """)
    List<OutboxEventJpaEntity> findDispatchable(@Param("now") Instant now, Pageable limit);
}
