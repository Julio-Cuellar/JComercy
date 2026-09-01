package com.jclinical.inventory.domain.ports.out;

import com.jclinical.inventory.domain.model.Material;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaterialRepositoryPort {

    Material save(Material material);

    Optional<Material> findByIdAndClinicId(UUID materialId, UUID clinicId);

    Optional<Material> findByIdAndClinicIdForUpdate(UUID materialId, UUID clinicId);

    List<Material> findByClinicId(UUID clinicId, boolean includeInactive);
}
