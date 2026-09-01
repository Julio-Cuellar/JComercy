package com.jcode.jcomercy.inventory.infra.persistence.adapter;

import com.jcode.jcomercy.inventory.domain.model.unit.UnitOfMeasure;
import com.jcode.jcomercy.inventory.domain.port.UnitOfMeasureRepositoryPort;
import com.jcode.jcomercy.inventory.infra.persistence.entity.UnitOfMeasureJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.UnitOfMeasureJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UnitOfMeasureRepositoryAdapter implements UnitOfMeasureRepositoryPort {
    private final UnitOfMeasureJpaRepository repository;
    public UnitOfMeasureRepositoryAdapter(UnitOfMeasureJpaRepository repository) { this.repository = repository; }

    @Override public Optional<UnitOfMeasure> findByCode(String code) {
        return repository.findByCodeAndStatus(code.trim().toUpperCase(), "ACTIVE").map(this::toDomain);
    }
    @Override public List<UnitOfMeasure> findActive() {
        return repository.findByStatus("ACTIVE").stream().map(this::toDomain).toList();
    }
    private UnitOfMeasure toDomain(UnitOfMeasureJpaEntity value) {
        return new UnitOfMeasure(value.getCode(), value.getName(), value.getSymbol(), value.getDimension(),
                value.getBaseUnitCode(), value.getConversionFactor(), value.getDecimals());
    }
}
