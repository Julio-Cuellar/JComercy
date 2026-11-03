package com.jcode.jcomercy.inventory.domain.model.unit;

import com.jcode.jcomercy.inventory.domain.exception.InventoryDomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record UnitOfMeasure(
        String code,
        String name,
        String symbol,
        UnitDimension dimension,
        String baseUnitCode,
        BigDecimal conversionFactor,
        int decimals
) {

    public UnitOfMeasure {
        code = normalize(code, "code");
        name = requireText(name, "name");
        symbol = requireText(symbol, "symbol");
        Objects.requireNonNull(dimension, "dimension must not be null");
        baseUnitCode = normalize(baseUnitCode, "baseUnitCode");
        Objects.requireNonNull(conversionFactor, "conversionFactor must not be null");
        if (conversionFactor.signum() <= 0) {
            throw new InventoryDomainException("conversionFactor must be greater than zero");
        }
        if (decimals < 0 || decimals > 9) {
            throw new InventoryDomainException("decimals must be between 0 and 9");
        }
    }

    public BigDecimal convertTo(UnitOfMeasure target, BigDecimal quantity) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        if (dimension != target.dimension) {
            throw new InventoryDomainException("Units must belong to the same dimension");
        }
        return quantity.multiply(conversionFactor)
                .divide(target.conversionFactor, target.decimals, RoundingMode.HALF_UP);
    }

    private static String normalize(String value, String field) {
        return requireText(value, field).toUpperCase();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InventoryDomainException(field + " must not be blank");
        }
        return value.trim();
    }
}
