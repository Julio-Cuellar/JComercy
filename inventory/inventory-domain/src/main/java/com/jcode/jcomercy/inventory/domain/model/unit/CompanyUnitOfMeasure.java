package com.jcode.jcomercy.inventory.domain.model.unit;

import com.jcode.jcomercy.inventory.domain.exception.InventoryDomainException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record CompanyUnitOfMeasure(UUID id, UUID publicUnitId, String companyId, String code, String name,
                                   String symbol, UnitDimension dimension, String baseUnitCode,
                                   BigDecimal conversionFactor, int decimals, UnitStatus status) {
    public CompanyUnitOfMeasure {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(publicUnitId, "publicUnitId must not be null");
        companyId = text(companyId, "companyId");
        code = text(code, "code").toUpperCase();
        name = text(name, "name");
        symbol = text(symbol, "symbol");
        Objects.requireNonNull(dimension, "dimension must not be null");
        baseUnitCode = text(baseUnitCode, "baseUnitCode").toUpperCase();
        Objects.requireNonNull(conversionFactor, "conversionFactor must not be null");
        if (conversionFactor.signum() <= 0) throw new InventoryDomainException("conversionFactor must be greater than zero");
        if (decimals < 0 || decimals > 9) throw new InventoryDomainException("decimals must be between 0 and 9");
        Objects.requireNonNull(status, "status must not be null");
    }
    private static String text(String value, String field) {
        if (value == null || value.isBlank()) throw new InventoryDomainException(field + " must not be blank");
        return value.trim();
    }
    public enum UnitStatus { ACTIVE, INACTIVE }
}
