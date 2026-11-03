package com.jcode.jcomercy.inventory.domain.port;

import com.jcode.jcomercy.inventory.domain.model.product.Product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepositoryPort {
    Product save(Product product);
    Optional<Product> findById(UUID id);
    List<Product> findByCompanyId(String companyId);
    boolean existsByCompanyIdAndSku(String companyId, String sku);
}
