package com.jcode.jcomercy.sales.application.service;

import com.jcode.jcomercy.sales.domain.model.price.ProductPrice;
import com.jcode.jcomercy.sales.domain.model.sale.Sale;
import com.jcode.jcomercy.sales.domain.port.ProductPriceRepositoryPort;
import com.jcode.jcomercy.sales.domain.port.SaleRepositoryPort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Puertos de ventas en memoria, para probar los casos de uso sin base de datos. */
final class InMemorySalesPorts {

    private InMemorySalesPorts() {
    }

    static final class Prices implements ProductPriceRepositoryPort {
        private final Map<String, ProductPrice> byKey = new LinkedHashMap<>();

        @Override
        public ProductPrice save(ProductPrice price) {
            byKey.put(price.companyId() + "/" + price.productId(), price);
            return price;
        }

        @Override
        public List<ProductPrice> findByCompanyId(String companyId) {
            return byKey.values().stream().filter(price -> price.companyId().equals(companyId)).toList();
        }

        @Override
        public List<ProductPrice> findByCompanyIdAndProductIds(String companyId, Collection<UUID> productIds) {
            return findByCompanyId(companyId).stream().filter(price -> productIds.contains(price.productId())).toList();
        }
    }

    static final class Sales implements SaleRepositoryPort {
        private final Map<UUID, Sale> byId = new LinkedHashMap<>();

        @Override
        public Sale save(Sale sale) {
            byId.put(sale.id(), sale);
            return sale;
        }

        @Override
        public Optional<Sale> findById(String companyId, String branchId, UUID id) {
            return Optional.ofNullable(byId.get(id))
                .filter(sale -> sale.companyId().equals(companyId) && sale.branchId().equals(branchId));
        }

        @Override
        public List<Sale> findByBranch(String companyId, String branchId) {
            return new ArrayList<>(byId.values().stream()
                .filter(sale -> sale.companyId().equals(companyId) && sale.branchId().equals(branchId))
                .sorted(Comparator.comparing(Sale::createdAt).reversed()).toList());
        }
    }
}
