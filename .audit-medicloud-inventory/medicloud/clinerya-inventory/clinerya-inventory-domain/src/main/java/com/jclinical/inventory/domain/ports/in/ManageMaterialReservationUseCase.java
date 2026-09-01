package com.jclinical.inventory.domain.ports.in;

import com.jclinical.inventory.domain.model.MaterialReservationDetail;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ManageMaterialReservationUseCase {

    void reserveForAppointment(UUID clinicId, UUID appointmentId, List<ReservationLineCommand> lines);

    void releaseForAppointment(UUID clinicId, UUID appointmentId);

    List<MaterialReservationDetail> listActiveReservations(UUID clinicId);

    record ReservationLineCommand(UUID materialId, String materialName, BigDecimal quantity) {}
}
