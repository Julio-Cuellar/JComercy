package com.jcode.jcomercy.inventory.domain.port;

import com.jcode.jcomercy.inventory.domain.model.unit.CompanyUnitOfMeasure;
import java.util.List;
import java.util.Optional;

public interface CompanyUnitOfMeasureRepositoryPort {
    CompanyUnitOfMeasure save(CompanyUnitOfMeasure unit);
    Optional<CompanyUnitOfMeasure> findActiveByCompanyIdAndCode(String companyId, String code);
    boolean existsByCompanyIdAndCode(String companyId, String code);
    List<CompanyUnitOfMeasure> findActiveByCompanyId(String companyId);
}
