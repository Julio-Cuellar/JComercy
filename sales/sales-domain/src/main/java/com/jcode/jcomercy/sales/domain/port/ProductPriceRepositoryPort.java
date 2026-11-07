package com.jcode.jcomercy.sales.domain.port;

import com.jcode.jcomercy.sales.domain.model.price.ProductPrice;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Los precios de venta de una empresa. Guardar crea el precio de un producto o lo reemplaza. */
public interface ProductPriceRepositoryPort {

    ProductPrice save(ProductPrice price);

    List<ProductPrice> findByCompanyId(String companyId);

    List<ProductPrice> findByCompanyIdAndProductIds(String companyId, Collection<UUID> productIds);
}
