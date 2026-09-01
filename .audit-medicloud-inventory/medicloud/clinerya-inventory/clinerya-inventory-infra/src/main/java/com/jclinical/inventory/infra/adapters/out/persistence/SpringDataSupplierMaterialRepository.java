package com.jclinical.inventory.infra.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataSupplierMaterialRepository extends JpaRepository<SupplierMaterialEntity, UUID> {
    Optional<SupplierMaterialEntity> findBySupplierIdAndMaterialId(UUID supplierId, UUID materialId);

    List<SupplierMaterialEntity> findBySupplierIdAndClinicIdAndActiveTrueOrderByMaterialNameAsc(
            UUID supplierId,
            UUID clinicId
    );
}
