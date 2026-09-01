package com.jcode.jcomercy.inventory.domain.port;

import com.jcode.jcomercy.inventory.domain.model.product.ProductUnitConversion;

import java.util.List;
import java.util.UUID;

public interface ProductUnitConversionRepositoryPort {
    ProductUnitConversion save(ProductUnitConversion conversion);
    boolean existsByProductIdAndFromUnitCodeAndToUnitCode(UUID productId, String fromUnitCode, String toUnitCode);
    List<ProductUnitConversion> findByProductId(UUID productId);
}
