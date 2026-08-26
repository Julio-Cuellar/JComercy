package com.jcode.jcomercy.companymanagement.infra.persistence.repository;

import com.jcode.jcomercy.companymanagement.infra.persistence.entity.AccountJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountSpringDataRepository extends JpaRepository<AccountJpaEntity, UUID> {
    Optional<AccountJpaEntity> findByPublicAccountId(String publicAccountId);
    boolean existsByPublicAccountId(String publicAccountId);
}
