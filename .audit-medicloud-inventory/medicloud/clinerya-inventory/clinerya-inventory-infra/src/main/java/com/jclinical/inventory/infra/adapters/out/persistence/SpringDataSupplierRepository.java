package com.jclinical.inventory.infra.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataSupplierRepository extends JpaRepository<SupplierEntity, UUID> {
    Optional<SupplierEntity> findByIdAndClinicId(UUID id, UUID clinicId);

    List<SupplierEntity> findByClinicIdOrderByNameAsc(UUID clinicId);
}
