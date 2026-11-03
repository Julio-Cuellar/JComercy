package com.jcode.jcomercy.inventory.domain.model.product;

import com.jcode.jcomercy.inventory.domain.exception.InventoryDomainException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    @Test
    void normalizesCatalogCodesAndExposesSerialTracking() {
        var product = new Product(UUID.randomUUID(), UUID.randomUUID(), "company-1", " sku-1 ", "Producto", null,
                "pza", "paquete", "pza", null, null, ProductTrackingMode.SERIAL, false, ProductStatus.ACTIVE);

        assertThat(product.sku()).isEqualTo("SKU-1");
        assertThat(product.stockUnitCode()).isEqualTo("PZA");
        assertThat(product.isSerialized()).isTrue();
    }

    @Test
    void requiresNetQuantityUnitWhenNetQuantityIsProvided() {
        assertThatThrownBy(() -> new Product(UUID.randomUUID(), UUID.randomUUID(), "company-1", "SKU-1", "Producto", null,
                "PZA", null, null, new java.math.BigDecimal("2"), null, ProductTrackingMode.NONE, false, ProductStatus.ACTIVE))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessage("netQuantityUnitCode is required when netQuantity is provided");
    }
}
