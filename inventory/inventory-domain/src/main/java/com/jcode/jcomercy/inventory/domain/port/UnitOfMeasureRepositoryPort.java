package com.jcode.jcomercy.inventory.domain.port;

import com.jcode.jcomercy.inventory.domain.model.unit.UnitOfMeasure;

import java.util.List;
import java.util.Optional;

public interface UnitOfMeasureRepositoryPort {
    Optional<UnitOfMeasure> findByCode(String code);
    List<UnitOfMeasure> findActive();
}
