package com.jcode.jcomercy.inventory.infra.persistence.adapter;

import com.jcode.jcomercy.inventory.domain.model.unit.CompanyUnitOfMeasure;
import com.jcode.jcomercy.inventory.domain.port.CompanyUnitOfMeasureRepositoryPort;
import com.jcode.jcomercy.inventory.infra.persistence.entity.CompanyUnitOfMeasureJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.CompanyUnitOfMeasureJpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public class CompanyUnitOfMeasureRepositoryAdapter implements CompanyUnitOfMeasureRepositoryPort {
    private final CompanyUnitOfMeasureJpaRepository repository;
    public CompanyUnitOfMeasureRepositoryAdapter(CompanyUnitOfMeasureJpaRepository repository) { this.repository=repository; }
    @Override public CompanyUnitOfMeasure save(CompanyUnitOfMeasure value) { return toDomain(repository.save(toEntity(value))); }
    @Override public Optional<CompanyUnitOfMeasure> findActiveByCompanyIdAndCode(String companyId, String code) {
        return repository.findByCompanyIdAndCodeAndStatus(companyId, code.trim().toUpperCase(), CompanyUnitOfMeasure.UnitStatus.ACTIVE).map(this::toDomain);
    }
    @Override public boolean existsByCompanyIdAndCode(String companyId, String code) { return repository.existsByCompanyIdAndCode(companyId, code.trim().toUpperCase()); }
    @Override public List<CompanyUnitOfMeasure> findActiveByCompanyId(String companyId) { return repository.findByCompanyIdAndStatus(companyId, CompanyUnitOfMeasure.UnitStatus.ACTIVE).stream().map(this::toDomain).toList(); }
    private CompanyUnitOfMeasureJpaEntity toEntity(CompanyUnitOfMeasure v) { return new CompanyUnitOfMeasureJpaEntity(v.id(),v.publicUnitId(),v.companyId(),v.code(),v.name(),v.symbol(),v.dimension(),v.baseUnitCode(),v.conversionFactor(),v.decimals(),v.status()); }
    private CompanyUnitOfMeasure toDomain(CompanyUnitOfMeasureJpaEntity v) { return new CompanyUnitOfMeasure(v.getId(),v.getPublicUnitId(),v.getCompanyId(),v.getCode(),v.getName(),v.getSymbol(),v.getDimension(),v.getBaseUnitCode(),v.getConversionFactor(),v.getDecimals(),v.getStatus()); }
}
