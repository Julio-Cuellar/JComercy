package com.jclinical.inventory.infra.adapters.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataPurchaseOrderRepository extends JpaRepository<PurchaseOrderEntity, UUID> {
    Optional<PurchaseOrderEntity> findByIdAndClinicId(UUID id, UUID clinicId);

    List<PurchaseOrderEntity> findByClinicIdOrderByCreatedAtDesc(UUID clinicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select purchaseOrder from PurchaseOrderEntity purchaseOrder "
            + "where purchaseOrder.id = :id and purchaseOrder.clinicId = :clinicId")
    Optional<PurchaseOrderEntity> findForUpdate(@Param("id") UUID id, @Param("clinicId") UUID clinicId);
}
