package com.jclinical.inventory.infra.config;

import com.jclinical.inventory.domain.model.MaterialReservationDetail;
import com.jclinical.inventory.domain.ports.in.ManageMaterialReservationUseCase;
import com.jclinical.inventory.domain.service.MaterialReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Primary
@RequiredArgsConstructor
public class TransactionalMaterialReservationUseCase implements ManageMaterialReservationUseCase {

    private final MaterialReservationService reservationService;

    @Override
    @Transactional
    public void reserveForAppointment(UUID clinicId, UUID appointmentId, List<ReservationLineCommand> lines) {
        reservationService.reserveForAppointment(clinicId, appointmentId, lines);
    }

    @Override
    @Transactional
    public void releaseForAppointment(UUID clinicId, UUID appointmentId) {
        reservationService.releaseForAppointment(clinicId, appointmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaterialReservationDetail> listActiveReservations(UUID clinicId) {
        return reservationService.listActiveReservations(clinicId);
    }
}
