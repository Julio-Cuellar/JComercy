package com.jclinical.inventory.domain.service;

import com.jclinical.inventory.domain.model.Material;
import com.jclinical.inventory.domain.model.MaterialReservation;
import com.jclinical.inventory.domain.model.MaterialReservationDetail;
import com.jclinical.inventory.domain.model.MaterialReservationStatus;
import com.jclinical.inventory.domain.ports.in.ManageMaterialReservationUseCase;
import com.jclinical.inventory.domain.ports.out.MaterialRepositoryPort;
import com.jclinical.inventory.domain.ports.out.MaterialReservationRepositoryPort;
import com.jclinical.inventory.domain.ports.out.MaterialReservationQueryPort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MaterialReservationService implements ManageMaterialReservationUseCase {

    private final MaterialRepositoryPort materialRepository;
    private final MaterialReservationRepositoryPort reservationRepository;
    private final MaterialReservationQueryPort reservationQuery;

    public MaterialReservationService(
            MaterialRepositoryPort materialRepository,
            MaterialReservationRepositoryPort reservationRepository,
            MaterialReservationQueryPort reservationQuery) {
        this.materialRepository = materialRepository;
        this.reservationRepository = reservationRepository;
        this.reservationQuery = reservationQuery;
    }

    @Override
    public void reserveForAppointment(UUID clinicId, UUID appointmentId, List<ReservationLineCommand> lines) {
        Map<UUID, ReservationDraft> aggregatedLines = aggregateLines(lines);

        for (ReservationDraft line : aggregatedLines.values()) {
            if (reservationRepository.existsByAppointmentIdAndMaterialId(appointmentId, line.materialId())) {
                continue;
            }

            Material material = materialRepository.findByIdAndClinicIdForUpdate(line.materialId(), clinicId)
                    .orElse(null);
            if (material == null || !material.isActive()) {
                continue;
            }

            material.reserve(line.quantity());
            materialRepository.save(material);

            MaterialReservation reservation = MaterialReservation.builder()
                    .id(UUID.randomUUID())
                    .clinicId(clinicId)
                    .appointmentId(appointmentId)
                    .materialId(line.materialId())
                    .materialName(line.materialName())
                    .quantity(line.quantity())
                    .status(MaterialReservationStatus.RESERVED)
                    .createdAt(LocalDateTime.now())
                    .build();
            reservationRepository.save(reservation);
        }
    }

    private Map<UUID, ReservationDraft> aggregateLines(List<ReservationLineCommand> lines) {
        Map<UUID, ReservationDraft> aggregated = new LinkedHashMap<>();
        if (lines == null) {
            return aggregated;
        }

        for (ReservationLineCommand line : lines) {
            if (line.materialId() == null || line.quantity() == null || line.quantity().signum() <= 0) {
                continue;
            }
            aggregated.merge(
                    line.materialId(),
                    new ReservationDraft(line.materialId(), line.materialName(), line.quantity()),
                    ReservationDraft::add
            );
        }
        return aggregated;
    }

    @Override
    public void releaseForAppointment(UUID clinicId, UUID appointmentId) {
        List<MaterialReservation> reservations = reservationRepository.findByAppointmentId(appointmentId);
        for (MaterialReservation reservation : reservations) {
            if (reservation.getStatus() != MaterialReservationStatus.RESERVED) {
                continue;
            }
            Material material = materialRepository.findByIdAndClinicIdForUpdate(reservation.getMaterialId(), clinicId)
                    .orElse(null);
            if (material != null) {
                material.releaseReservation(reservation.getQuantity());
                materialRepository.save(material);
            }
            reservation.release();
            reservationRepository.save(reservation);
        }
    }

    @Override
    public List<MaterialReservationDetail> listActiveReservations(UUID clinicId) {
        return reservationQuery.findActiveByClinicId(clinicId);
    }

    private record ReservationDraft(UUID materialId, String materialName, BigDecimal quantity) {
        private ReservationDraft add(ReservationDraft other) {
            return new ReservationDraft(materialId, materialName, quantity.add(other.quantity()));
        }
    }
}
