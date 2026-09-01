package com.jclinical.inventory.domain.ports.out;

import com.jclinical.inventory.domain.model.Supplier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepositoryPort {
    Supplier save(Supplier supplier);

    Optional<Supplier> findByIdAndClinicId(UUID supplierId, UUID clinicId);

    List<Supplier> findByClinicId(UUID clinicId);
}
