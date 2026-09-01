package com.jclinical.inventory.domain.ports.out;

import com.jclinical.inventory.domain.model.MaterialReservationDetail;

import java.util.List;
import java.util.UUID;

public interface MaterialReservationQueryPort {

    List<MaterialReservationDetail> findActiveByClinicId(UUID clinicId);
}
