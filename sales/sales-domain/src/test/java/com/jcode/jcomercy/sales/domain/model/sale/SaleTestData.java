package com.jcode.jcomercy.sales.domain.model.sale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Datos de ejemplo compartidos por las pruebas de la venta. */
final class SaleTestData {

    static final Instant NOW = Instant.parse("2026-11-07T18:00:00Z");

    private SaleTestData() {
    }

    static SaleLine line(String sku, String quantity, String unitPrice) {
        return SaleLine.of(UUID.randomUUID(), sku, "Producto " + sku, new BigDecimal(quantity), new BigDecimal(unitPrice));
    }

    /** Dos cafés a 45.50 y una galleta a 10.00: 101.00 en total. */
    static Sale pendingSale() {
        return Sale.create(UUID.randomUUID(), "cmp_1", "br_1", UUID.randomUUID(), SaleChannel.MOSTRADOR, null, "MXN",
            List.of(line("CAFE", "2", "45.50"), line("GALLETA", "1", "10.00")), "usr_cajera", NOW);
    }

    static Payment cash(String amount) {
        return new Payment(PaymentMethod.CASH, new BigDecimal(amount), null);
    }

    static Payment card(String amount) {
        return new Payment(PaymentMethod.CARD, new BigDecimal(amount), "AUT-123456");
    }
}
