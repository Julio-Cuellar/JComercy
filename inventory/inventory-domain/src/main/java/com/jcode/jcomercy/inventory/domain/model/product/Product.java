package com.jcode.jcomercy.inventory.domain.model.product;

import com.jcode.jcomercy.inventory.domain.exception.InventoryDomainException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record Product(
        UUID id,
        UUID publicProductId,
        String companyId,
        String sku,
        String name,
        String categoryId,
        String stockUnitCode,
        String purchaseUnitCode,
        String saleUnitCode,
        BigDecimal netQuantity,
        String netQuantityUnitCode,
        ProductTrackingMode trackingMode,
        boolean allowNegativeStock,
        ProductStatus status
) {

    public Product {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(publicProductId, "publicProductId must not be null");
        companyId = required(companyId, "companyId");
        sku = required(sku, "sku").toUpperCase();
        name = required(name, "name");
        stockUnitCode = required(stockUnitCode, "stockUnitCode").toUpperCase();
        purchaseUnitCode = optionalUpper(purchaseUnitCode);
        saleUnitCode = optionalUpper(saleUnitCode);
        netQuantityUnitCode = optionalUpper(netQuantityUnitCode);
        Objects.requireNonNull(trackingMode, "trackingMode must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (netQuantity != null && netQuantity.signum() <= 0) {
            throw new InventoryDomainException("netQuantity must be greater than zero");
        }
        if (netQuantity != null && netQuantityUnitCode == null) {
            throw new InventoryDomainException("netQuantityUnitCode is required when netQuantity is provided");
        }
    }

    public boolean isSerialized() {
        return trackingMode.requiresSerial();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InventoryDomainException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalUpper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }
}
