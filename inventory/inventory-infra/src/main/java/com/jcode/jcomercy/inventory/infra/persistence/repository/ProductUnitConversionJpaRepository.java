package com.jcode.jcomercy.inventory.infra.persistence.repository;

import com.jcode.jcomercy.inventory.infra.persistence.entity.ProductUnitConversionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

public interface ProductUnitConversionJpaRepository extends JpaRepository<ProductUnitConversionJpaEntity, UUID> {
    boolean existsByProductIdAndFromUnitCodeAndToUnitCode(UUID productId, String fromUnitCode, String toUnitCode);
    List<ProductUnitConversionJpaEntity> findByProductId(UUID productId);
}
