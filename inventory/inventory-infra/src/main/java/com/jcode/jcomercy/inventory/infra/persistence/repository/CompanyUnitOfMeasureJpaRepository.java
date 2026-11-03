package com.jcode.jcomercy.inventory.infra.persistence.repository;

import com.jcode.jcomercy.inventory.domain.model.unit.CompanyUnitOfMeasure.UnitStatus;
import com.jcode.jcomercy.inventory.infra.persistence.entity.CompanyUnitOfMeasureJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyUnitOfMeasureJpaRepository extends JpaRepository<CompanyUnitOfMeasureJpaEntity, UUID> {
    Optional<CompanyUnitOfMeasureJpaEntity> findByCompanyIdAndCodeAndStatus(String companyId, String code, UnitStatus status);
    boolean existsByCompanyIdAndCode(String companyId, String code);
    List<CompanyUnitOfMeasureJpaEntity> findByCompanyIdAndStatus(String companyId, UnitStatus status);
}
