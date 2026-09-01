package com.jclinical.inventory.domain.ports.out;

import com.jclinical.inventory.domain.model.InventoryMovement;
import com.jclinical.inventory.domain.model.MovementType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryMovementRepositoryPort {

    InventoryMovement save(InventoryMovement movement);

    List<InventoryMovement> findByMaterialIdAndClinicId(UUID materialId, UUID clinicId, int page, int size);

    List<InventoryMovement> findByClinicId(UUID clinicId, int page, int size);

    Optional<InventoryMovement> findByReferenceAndMaterial(
            MovementType type,
            String referenceType,
            UUID referenceId,
            UUID materialId
    );
}
