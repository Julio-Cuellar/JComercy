package com.jcode.jcomercy.inventory.infra.persistence.repository;

import com.jcode.jcomercy.inventory.domain.model.stock.ReservationStatus;
import com.jcode.jcomercy.inventory.infra.persistence.entity.StockReservationJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StockReservationJpaRepository extends JpaRepository<StockReservationJpaEntity, UUID> {
    List<StockReservationJpaEntity> findByCompanyIdAndReferenceTypeAndReferenceIdAndStatus(
            String companyId, String referenceType, String referenceId, ReservationStatus status);

    boolean existsByCompanyIdAndReferenceTypeAndReferenceIdAndStatus(
            String companyId, String referenceType, String referenceId, ReservationStatus status);

    @Query("""
            select distinct r.stockBalanceId from StockReservationJpaEntity r
             where r.companyId = :companyId and r.referenceType = :referenceType
               and r.referenceId = :referenceId and r.status = :status
             order by r.stockBalanceId
            """)
    List<UUID> findBalanceIdsByReference(@Param("companyId") String companyId,
                                         @Param("referenceType") String referenceType,
                                         @Param("referenceId") String referenceId,
                                         @Param("status") ReservationStatus status);

    List<StockReservationJpaEntity> findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            ReservationStatus status, Instant now, Pageable pageable);

    @Query("""
            select distinct r.stockBalanceId from StockReservationJpaEntity r
             where r.status = :status and r.expiresAt <= :now
             order by r.stockBalanceId
            """)
    List<UUID> findDueBalanceIds(@Param("status") ReservationStatus status, @Param("now") Instant now,
                                 Pageable pageable);
}
