package com.jcode.jcomercy.sales.application.service;

import com.jcode.jcomercy.sales.domain.exception.DuplicateSaleException;
import com.jcode.jcomercy.sales.domain.model.price.ProductPrice;
import com.jcode.jcomercy.sales.domain.model.sale.Sale;
import com.jcode.jcomercy.sales.domain.port.FolioGeneratorPort;
import com.jcode.jcomercy.sales.domain.port.ProductPriceRepositoryPort;
import com.jcode.jcomercy.sales.domain.port.SaleRepositoryPort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
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

    static final class Folios implements FolioGeneratorPort {
        private final Map<String, Integer> counters = new HashMap<>();

        @Override
        public String next(String companyId, String branchId) {
            int next = counters.merge(companyId + "/" + branchId, 1, Integer::sum);
            return "TCK-%06d".formatted(next);
        }
    }

    static final class Sales implements SaleRepositoryPort {
        private final Map<UUID, Sale> byId = new LinkedHashMap<>();
        private final Map<String, UUID> byKey = new HashMap<>();

        @Override
        public Sale save(Sale sale) {
            byId.put(sale.id(), sale);
            return sale;
        }

        @Override
        public Sale create(Sale sale, String idempotencyKey) {
            String key = sale.companyId() + "/" + sale.branchId() + "/" + idempotencyKey;
            if (byKey.containsKey(key)) {
                throw new DuplicateSaleException();
            }
            byKey.put(key, sale.id());
            return save(sale);
        }

        @Override
        public Optional<Sale> findByIdempotencyKey(String companyId, String branchId, String idempotencyKey) {
            UUID id = byKey.get(companyId + "/" + branchId + "/" + idempotencyKey);
            return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
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
