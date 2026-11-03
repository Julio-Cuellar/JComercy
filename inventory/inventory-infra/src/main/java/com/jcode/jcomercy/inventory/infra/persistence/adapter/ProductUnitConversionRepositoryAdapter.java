package com.jcode.jcomercy.inventory.infra.persistence.adapter;

import com.jcode.jcomercy.inventory.domain.model.product.ProductUnitConversion;
import com.jcode.jcomercy.inventory.domain.port.ProductUnitConversionRepositoryPort;
import com.jcode.jcomercy.inventory.infra.persistence.entity.ProductUnitConversionJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.ProductUnitConversionJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class ProductUnitConversionRepositoryAdapter implements ProductUnitConversionRepositoryPort {
    private final ProductUnitConversionJpaRepository repository;
    public ProductUnitConversionRepositoryAdapter(ProductUnitConversionJpaRepository repository) { this.repository = repository; }
    @Override public ProductUnitConversion save(ProductUnitConversion value) { return toDomain(repository.save(toEntity(value))); }
    @Override public boolean existsByProductIdAndFromUnitCodeAndToUnitCode(UUID productId, String from, String to) {
        return repository.existsByProductIdAndFromUnitCodeAndToUnitCode(productId, from.trim().toUpperCase(), to.trim().toUpperCase());
    }
    @Override public List<ProductUnitConversion> findByProductId(UUID productId) { return repository.findByProductId(productId).stream().map(this::toDomain).toList(); }
    private ProductUnitConversionJpaEntity toEntity(ProductUnitConversion value) {
        return new ProductUnitConversionJpaEntity(value.id(), value.productId(), value.fromUnitCode(), value.toUnitCode(),
                value.factor(), value.roundingScale(), value.roundingMode(), value.purchaseUnit(), value.salesUnit(),
                value.defaultConversion(), value.status());
    }
    private ProductUnitConversion toDomain(ProductUnitConversionJpaEntity value) {
        return new ProductUnitConversion(value.getId(), value.getProductId(), value.getFromUnitCode(), value.getToUnitCode(),
                value.getFactor(), value.getRoundingScale(), value.getRoundingMode(), value.isPurchaseUnit(),
                value.isSalesUnit(), value.isDefaultConversion(), value.getStatus());
    }
}
