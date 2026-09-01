package com.jclinical.inventory.infra.adapters.out.persistence;

import com.jclinical.inventory.domain.model.MaterialReservation;

public interface MaterialReservationMapper {

    MaterialReservationEntity toEntity(MaterialReservation domain);

    MaterialReservation toDomain(MaterialReservationEntity entity);
}
