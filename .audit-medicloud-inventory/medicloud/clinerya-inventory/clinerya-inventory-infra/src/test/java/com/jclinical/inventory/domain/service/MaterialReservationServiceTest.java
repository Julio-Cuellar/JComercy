package com.jclinical.inventory.domain.service;

import com.jclinical.inventory.domain.model.MaterialReservationDetail;
import com.jclinical.inventory.domain.model.MaterialReservationStatus;
import com.jclinical.inventory.domain.ports.out.MaterialRepositoryPort;
import com.jclinical.inventory.domain.ports.out.MaterialReservationQueryPort;
import com.jclinical.inventory.domain.ports.out.MaterialReservationRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialReservationServiceTest {

    @Mock
    private MaterialRepositoryPort materialRepository;
    @Mock
    private MaterialReservationRepositoryPort reservationRepository;
    @Mock
    private MaterialReservationQueryPort reservationQuery;

    private MaterialReservationService service;

    @BeforeEach
    void setUp() {
        service = new MaterialReservationService(materialRepository, reservationRepository, reservationQuery);
    }

    @Test
    void listsActiveReservationsWithPatientAndTreatmentDestination() {
        UUID clinicId = UUID.randomUUID();
        MaterialReservationDetail reservation = new MaterialReservationDetail(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Mariana Torres Vega",
                "Endodoncia molar",
                LocalDateTime.of(2026, 7, 14, 12, 0),
                UUID.randomUUID(),
                "Lidocaina 2% con epinefrina",
                "cartucho",
                new BigDecimal("2.0000"),
                new BigDecimal("98.0000"),
                new BigDecimal("2.0000"),
                new BigDecimal("96.0000"),
                MaterialReservationStatus.RESERVED,
                LocalDateTime.of(2026, 7, 10, 9, 0)
        );
        when(reservationQuery.findActiveByClinicId(clinicId)).thenReturn(List.of(reservation));

        List<MaterialReservationDetail> result = service.listActiveReservations(clinicId);

        assertThat(result).containsExactly(reservation);
        assertThat(result.getFirst().patientName()).isEqualTo("Mariana Torres Vega");
        assertThat(result.getFirst().treatmentName()).isEqualTo("Endodoncia molar");
        verify(reservationQuery).findActiveByClinicId(clinicId);
    }
}
