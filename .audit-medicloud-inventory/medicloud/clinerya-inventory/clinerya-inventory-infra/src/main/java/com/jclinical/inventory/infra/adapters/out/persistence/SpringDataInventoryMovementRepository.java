package com.jclinical.inventory.infra.adapters.out.persistence;

import com.jclinical.inventory.domain.model.MovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataInventoryMovementRepository extends JpaRepository<InventoryMovementEntity, UUID> {

    Page<InventoryMovementEntity> findByMaterialIdAndClinicIdOrderByMovementDateDescCreatedAtDesc(
            UUID materialId,
            UUID clinicId,
            Pageable pageable
    );

    Page<InventoryMovementEntity> findByClinicIdOrderByMovementDateDescCreatedAtDesc(
            UUID clinicId,
            Pageable pageable
    );

    Optional<InventoryMovementEntity> findByTypeAndReferenceTypeAndReferenceIdAndMaterialId(
            MovementType type,
            String referenceType,
            UUID referenceId,
            UUID materialId
    );
}
