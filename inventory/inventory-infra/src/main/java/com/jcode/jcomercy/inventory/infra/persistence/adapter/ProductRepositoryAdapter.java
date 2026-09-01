package com.jcode.jcomercy.inventory.infra.persistence.adapter;

import com.jcode.jcomercy.inventory.domain.model.product.Product;
import com.jcode.jcomercy.inventory.domain.port.ProductRepositoryPort;
import com.jcode.jcomercy.inventory.infra.persistence.entity.ProductJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.ProductJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProductRepositoryAdapter implements ProductRepositoryPort {
    private final ProductJpaRepository repository;

    public ProductRepositoryAdapter(ProductJpaRepository repository) { this.repository = repository; }

    @Override public Product save(Product product) { return toDomain(repository.save(toEntity(product))); }
    @Override public Optional<Product> findById(UUID id) { return repository.findById(id).map(this::toDomain); }
    @Override public List<Product> findByCompanyId(String companyId) { return repository.findByCompanyId(companyId).stream().map(this::toDomain).toList(); }
    @Override public boolean existsByCompanyIdAndSku(String companyId, String sku) { return repository.existsByCompanyIdAndSku(companyId, sku); }

    private ProductJpaEntity toEntity(Product value) {
        return new ProductJpaEntity(value.id(), value.publicProductId(), value.companyId(), value.sku(), value.name(),
                value.categoryId(), value.stockUnitCode(), value.purchaseUnitCode(), value.saleUnitCode(),
                value.netQuantity(), value.netQuantityUnitCode(), value.trackingMode(), value.allowNegativeStock(),
                "DEFAULT", value.status());
    }

    private Product toDomain(ProductJpaEntity value) {
        return new Product(value.getId(), value.getPublicProductId(), value.getCompanyId(), value.getSku(), value.getName(),
                value.getCategoryId(), value.getStockUnitCode(), value.getPurchaseUnitCode(), value.getSaleUnitCode(),
                value.getNetQuantity(), value.getNetQuantityUnitCode(), value.getTrackingMode(), value.isAllowNegativeStock(),
                value.getStatus());
    }
}
