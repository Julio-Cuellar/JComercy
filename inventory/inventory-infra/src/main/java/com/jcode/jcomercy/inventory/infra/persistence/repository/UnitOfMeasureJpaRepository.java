package com.jcode.jcomercy.inventory.infra.persistence.repository;

import com.jcode.jcomercy.inventory.infra.persistence.entity.UnitOfMeasureJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UnitOfMeasureJpaRepository extends JpaRepository<UnitOfMeasureJpaEntity, String> {
    List<UnitOfMeasureJpaEntity> findByStatus(String status);
    java.util.Optional<UnitOfMeasureJpaEntity> findByCodeAndStatus(String code, String status);
}
