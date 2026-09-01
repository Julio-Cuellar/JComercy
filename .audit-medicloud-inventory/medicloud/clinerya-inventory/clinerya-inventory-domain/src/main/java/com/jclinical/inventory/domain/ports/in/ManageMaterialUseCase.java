package com.jclinical.inventory.domain.ports.in;

import com.jclinical.inventory.domain.model.Material;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ManageMaterialUseCase {

    Material createMaterial(UUID clinicId, CreateMaterialCommand command);

    Material updateMaterial(UUID materialId, UUID clinicId, UpdateMaterialCommand command);

    void deactivateMaterial(UUID materialId, UUID clinicId);

    Optional<Material> getMaterial(UUID materialId, UUID clinicId);

    List<Material> getMaterialsByClinic(UUID clinicId, boolean includeInactive);

    record CreateMaterialCommand(
            String name,
            String category,
            String internalCode,
            String brand,
            String description,
            String unitOfMeasure,
            String presentationName,
            BigDecimal quantityPerPresentation,
            BigDecimal unitCost,
            BigDecimal minimumStock,
            boolean saleEnabled,
            BigDecimal salePrice,
            boolean tracksBatches
    ) {}

    record UpdateMaterialCommand(
            String name,
            String category,
            String internalCode,
            String brand,
            String description,
            String unitOfMeasure,
            String presentationName,
            BigDecimal quantityPerPresentation,
            BigDecimal unitCost,
            BigDecimal minimumStock,
            boolean saleEnabled,
            BigDecimal salePrice,
            boolean tracksBatches,
            boolean active
    ) {}
}
