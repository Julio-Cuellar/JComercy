package com.jcode.jcomercy.sales.domain.model.price;

import com.jcode.jcomercy.sales.domain.exception.SalesDomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * El precio de venta de un producto en una empresa: final, con IVA incluido y sin desglose, en centavos y en una moneda
 * de tres letras. Las ventas guardan el precio con que se vendieron, así que cambiarlo no altera las ya hechas.
 */
public record ProductPrice(String companyId, UUID productId, BigDecimal amount, String currency, Instant updatedAt,
                           String updatedBy) {
    private static final Pattern CURRENCY = Pattern.compile("[A-Za-z]{3}");

    public ProductPrice {
        if (companyId == null || companyId.isBlank()) {
            throw new SalesDomainException("Falta la empresa del precio.");
        }
        if (productId == null) {
            throw new SalesDomainException("Falta el producto del precio.");
        }
        if (amount == null) {
            throw new SalesDomainException("Falta el importe del precio.");
        }
        if (amount.signum() < 0) {
            throw new SalesDomainException("El precio no puede ser negativo.");
        }
        if (currency == null || !CURRENCY.matcher(currency.trim()).matches()) {
            throw new SalesDomainException("La moneda debe ser un código de tres letras, por ejemplo MXN.");
        }
        if (updatedBy == null || updatedBy.isBlank()) {
            throw new SalesDomainException("Falta quién fija el precio.");
        }
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        companyId = companyId.trim();
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        currency = currency.trim().toUpperCase();
        updatedBy = updatedBy.trim();
    }
}
