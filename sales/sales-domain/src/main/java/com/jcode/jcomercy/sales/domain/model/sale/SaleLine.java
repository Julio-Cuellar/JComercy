package com.jcode.jcomercy.sales.domain.model.sale;

import com.jcode.jcomercy.sales.domain.exception.SalesDomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/**
 * Una línea de la venta. Guarda el nombre y el SKU del producto y el precio con que se vendió, así que la venta se lee
 * igual aunque el producto o su precio cambien después. El importe se redondea a centavos, hacia arriba desde la mitad.
 */
public record SaleLine(int lineNumber, UUID productId, String sku, String name, BigDecimal quantity, BigDecimal unitPrice) {

    public SaleLine {
        Objects.requireNonNull(productId, "productId must not be null");
        if (sku == null || sku.isBlank()) {
            throw new SalesDomainException("Cada línea necesita el SKU de su producto.");
        }
        if (name == null || name.isBlank()) {
            throw new SalesDomainException("Cada línea necesita el nombre de su producto.");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new SalesDomainException("La cantidad de %s debe ser mayor que cero.".formatted(sku));
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new SalesDomainException("El precio de %s no puede ser negativo.".formatted(sku));
        }
        sku = sku.trim();
        name = name.trim();
        unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
    }

    /** Una línea todavía sin número: la venta la numera al armarse. */
    public static SaleLine of(UUID productId, String sku, String name, BigDecimal quantity, BigDecimal unitPrice) {
        return new SaleLine(0, productId, sku, name, quantity, unitPrice);
    }

    public BigDecimal lineTotal() {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    SaleLine numbered(int number) {
        return new SaleLine(number, productId, sku, name, quantity, unitPrice);
    }
}
