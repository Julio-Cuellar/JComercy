package com.jclinical.inventory.infra.adapters.out.persistence;

import com.jclinical.inventory.domain.model.MaterialReservation;
import com.jclinical.inventory.domain.ports.out.MaterialReservationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SqlMaterialReservationRepository implements MaterialReservationRepositoryPort {

    private final SpringDataMaterialReservationRepository springRepository;
    private final MaterialReservationMapper mapper;

    @Override
    public MaterialReservation save(MaterialReservation reservation) {
        MaterialReservationEntity entity = mapper.toEntity(reservation);
        MaterialReservationEntity saved = springRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public boolean existsByAppointmentIdAndMaterialId(UUID appointmentId, UUID materialId) {
        return springRepository.existsByAppointmentIdAndMaterialId(appointmentId, materialId);
    }

    @Override
    public List<MaterialReservation> findByAppointmentId(UUID appointmentId) {
        return springRepository.findByAppointmentId(appointmentId).stream().map(mapper::toDomain).toList();
    }
}
