package com.jclinical.inventory.domain.ports.out;

import com.jclinical.inventory.domain.model.MaterialReservation;

import java.util.List;
import java.util.UUID;

public interface MaterialReservationRepositoryPort {

    MaterialReservation save(MaterialReservation reservation);

    boolean existsByAppointmentIdAndMaterialId(UUID appointmentId, UUID materialId);

    List<MaterialReservation> findByAppointmentId(UUID appointmentId);
}
