package com.jclinical.inventory.infra.adapters.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataInventoryBatchRepository extends JpaRepository<InventoryBatchEntity, UUID> {

    Optional<InventoryBatchEntity> findByIdAndClinicId(UUID id, UUID clinicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBatchEntity b where b.id = :id and b.clinicId = :clinicId")
    Optional<InventoryBatchEntity> findByIdAndClinicIdForUpdate(
            @Param("id") UUID id,
            @Param("clinicId") UUID clinicId);

    @Query("select b from InventoryBatchEntity b where b.materialId = :materialId and b.clinicId = :clinicId "
            + "order by case when b.expirationDate is null then 1 else 0 end asc, b.expirationDate asc, b.createdAt asc")
    List<InventoryBatchEntity> findByMaterialIdAndClinicIdOrderByExpirationAsc(
            @Param("materialId") UUID materialId,
            @Param("clinicId") UUID clinicId);

    @Query("select b from InventoryBatchEntity b where b.materialId = :materialId and b.clinicId = :clinicId "
            + "and b.remainingQuantity >= :quantity "
            + "order by case when b.expirationDate is null then 1 else 0 end asc, b.expirationDate asc, b.createdAt asc")
    List<InventoryBatchEntity> findAvailableForConsumption(
            @Param("materialId") UUID materialId,
            @Param("clinicId") UUID clinicId,
            @Param("quantity") BigDecimal quantity,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBatchEntity b where b.materialId = :materialId and b.clinicId = :clinicId "
            + "and b.remainingQuantity > 0 "
            + "order by case when b.expirationDate is null then 1 else 0 end asc, b.expirationDate asc, b.createdAt asc")
    List<InventoryBatchEntity> findAvailableForConsumptionForUpdate(
            @Param("materialId") UUID materialId,
            @Param("clinicId") UUID clinicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBatchEntity b where b.clinicId = :clinicId "
            + "and b.expirationDate is not null and b.expirationDate <= :asOfDate "
            + "and b.remainingQuantity > 0 "
            + "order by b.expirationDate asc, b.createdAt asc")
    List<InventoryBatchEntity> findExpiredWithRemainingForUpdate(
            @Param("clinicId") UUID clinicId,
            @Param("asOfDate") LocalDate asOfDate);
}
