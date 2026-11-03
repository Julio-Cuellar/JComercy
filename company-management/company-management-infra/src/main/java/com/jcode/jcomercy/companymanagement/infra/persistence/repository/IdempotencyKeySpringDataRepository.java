package com.jcode.jcomercy.companymanagement.infra.persistence.repository;

import com.jcode.jcomercy.companymanagement.infra.persistence.entity.IdempotencyKeyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface IdempotencyKeySpringDataRepository extends JpaRepository<IdempotencyKeyJpaEntity, UUID> {
    boolean existsByOperationAndKey(String operation, String key);
    void deleteByOperationAndKey(String operation, String key);
}
