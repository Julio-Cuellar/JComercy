package com.jclinical.inventory.infra.adapters.in.web;

import com.jclinical.inventory.domain.model.MaterialReservationDetail;
import com.jclinical.inventory.domain.ports.in.ManageMaterialReservationUseCase;
import com.jclinical.inventory.infra.adapters.in.web.dto.MaterialReservationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/material-reservations")
@RequiredArgsConstructor
public class MaterialReservationController {

    private final ManageMaterialReservationUseCase reservationUseCase;

    @GetMapping
    public ResponseEntity<List<MaterialReservationResponse>> listActiveReservations(@PathVariable UUID clinicId) {
        List<MaterialReservationResponse> response = reservationUseCase.listActiveReservations(clinicId).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    private MaterialReservationResponse toResponse(MaterialReservationDetail reservation) {
        return new MaterialReservationResponse(
                reservation.reservationId(),
                reservation.appointmentId(),
                reservation.patientId(),
                reservation.patientName(),
                reservation.treatmentName(),
                reservation.scheduledStart(),
                reservation.materialId(),
                reservation.materialName(),
                reservation.unitOfMeasure(),
                reservation.quantity(),
                reservation.currentStock(),
                reservation.totalReservedQuantity(),
                reservation.availableQuantity(),
                reservation.status(),
                reservation.createdAt()
        );
    }
}
