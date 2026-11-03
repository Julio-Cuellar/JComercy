package com.jcode.jcomercy.companymanagement.infra.persistence.repository;

import com.jcode.jcomercy.companymanagement.infra.persistence.entity.BranchJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BranchSpringDataRepository extends JpaRepository<BranchJpaEntity, UUID> {
    Optional<BranchJpaEntity> findByPublicBranchId(String publicBranchId);
    Optional<BranchJpaEntity> findByPublicCompanyIdAndPublicBranchId(String publicCompanyId, String publicBranchId);
    List<BranchJpaEntity> findByPublicCompanyId(String publicCompanyId);
    Optional<BranchJpaEntity> findByPublicCompanyIdAndIsHeadOfficeTrue(String publicCompanyId);
    boolean existsByPublicCompanyIdAndCode(String publicCompanyId, String code);
}
