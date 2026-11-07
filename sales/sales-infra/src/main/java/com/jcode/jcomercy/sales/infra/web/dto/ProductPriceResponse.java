package com.jcode.jcomercy.sales.infra.web.dto;

import com.jcode.jcomercy.sales.domain.model.price.ProductPrice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** El precio de un producto como lo describe el contrato REST. */
public record ProductPriceResponse(UUID productId, BigDecimal amount, String currency, Instant updatedAt) {

    public static ProductPriceResponse from(ProductPrice price) {
        return new ProductPriceResponse(price.productId(), price.amount(), price.currency(), price.updatedAt());
    }
}
