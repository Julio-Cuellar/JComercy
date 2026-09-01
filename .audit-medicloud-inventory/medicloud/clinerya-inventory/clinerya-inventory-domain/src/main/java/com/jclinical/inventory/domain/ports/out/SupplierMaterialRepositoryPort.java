package com.jclinical.inventory.domain.ports.out;

import com.jclinical.inventory.domain.model.SupplierMaterial;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierMaterialRepositoryPort {
    SupplierMaterial save(SupplierMaterial supplierMaterial);

    Optional<SupplierMaterial> findBySupplierIdAndMaterialId(UUID supplierId, UUID materialId);

    List<SupplierMaterial> findActiveBySupplierIdAndClinicId(UUID supplierId, UUID clinicId);
}
