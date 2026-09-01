package com.jcode.jcomercy.companymanagement.infra.persistence.repository;

import com.jcode.jcomercy.companymanagement.infra.persistence.entity.CompanyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanySpringDataRepository extends JpaRepository<CompanyJpaEntity, UUID> {
    Optional<CompanyJpaEntity> findByPublicCompanyId(String publicCompanyId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CompanyJpaEntity> findForUpdateByPublicCompanyId(String publicCompanyId);
    List<CompanyJpaEntity> findByPublicAccountId(String publicAccountId);
    boolean existsByPublicCompanyId(String publicCompanyId);
}
