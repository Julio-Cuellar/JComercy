package com.jcode.jcomercy.inventory.domain.model.product;

import com.jcode.jcomercy.inventory.domain.exception.InventoryDomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

public record ProductUnitConversion(UUID id, UUID productId, String fromUnitCode, String toUnitCode,
                                    BigDecimal factor, int roundingScale, RoundingMode roundingMode,
                                    boolean purchaseUnit, boolean salesUnit, boolean defaultConversion,
                                    ConversionStatus status) {
    public ProductUnitConversion {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(productId, "productId must not be null");
        fromUnitCode = code(fromUnitCode, "fromUnitCode");
        toUnitCode = code(toUnitCode, "toUnitCode");
        if (fromUnitCode.equals(toUnitCode)) throw new InventoryDomainException("conversion units must be different");
        Objects.requireNonNull(factor, "factor must not be null");
        if (factor.signum() <= 0) throw new InventoryDomainException("factor must be greater than zero");
        if (roundingScale < 0 || roundingScale > 9) throw new InventoryDomainException("roundingScale must be between 0 and 9");
        Objects.requireNonNull(roundingMode, "roundingMode must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
    private static String code(String value, String field) {
        if (value == null || value.isBlank()) throw new InventoryDomainException(field + " must not be blank");
        return value.trim().toUpperCase();
    }

    public enum ConversionStatus { ACTIVE, INACTIVE }
}
