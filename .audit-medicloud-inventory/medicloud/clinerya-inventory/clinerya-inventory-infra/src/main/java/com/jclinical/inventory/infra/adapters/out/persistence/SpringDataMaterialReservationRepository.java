package com.jclinical.inventory.infra.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataMaterialReservationRepository extends JpaRepository<MaterialReservationEntity, UUID> {

    boolean existsByAppointmentIdAndMaterialId(UUID appointmentId, UUID materialId);

    List<MaterialReservationEntity> findByAppointmentId(UUID appointmentId);
}
