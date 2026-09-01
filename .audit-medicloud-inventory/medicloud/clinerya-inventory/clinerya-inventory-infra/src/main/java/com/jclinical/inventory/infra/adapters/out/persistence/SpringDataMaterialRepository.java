package com.jclinical.inventory.infra.adapters.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataMaterialRepository extends JpaRepository<MaterialEntity, UUID> {

    Optional<MaterialEntity> findByIdAndClinicId(UUID id, UUID clinicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MaterialEntity m where m.id = :id and m.clinicId = :clinicId")
    Optional<MaterialEntity> findByIdAndClinicIdForUpdate(@Param("id") UUID id, @Param("clinicId") UUID clinicId);

    List<MaterialEntity> findByClinicId(UUID clinicId);

    List<MaterialEntity> findByClinicIdAndActiveTrue(UUID clinicId);
}
