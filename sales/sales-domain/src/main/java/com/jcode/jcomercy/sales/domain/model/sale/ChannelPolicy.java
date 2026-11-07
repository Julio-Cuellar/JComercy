package com.jcode.jcomercy.sales.domain.model.sale;

/**
 * Lo que cambia de un canal de venta a otro: de dónde sale el precio, quién registra el pago y qué pasa si falta
 * stock. Hoy solo existe la política del mostrador; un canal externo traerá la suya con su conector.
 */
public record ChannelPolicy(PriceSource priceSource, PaymentCollection paymentCollection, StockShortage stockShortage) {

    /** De dónde sale el precio de cada línea. */
    public enum PriceSource {
        /** De la lista de precios de la empresa. */
        CATALOG
    }

    /** Quién registra el cobro. */
    public enum PaymentCollection {
        /** Se registra dentro de la propia venta. */
        REGISTERED_IN_SALE
    }

    /** Qué pasa si no alcanza el stock. */
    public enum StockShortage {
        /** No se vende nada. */
        REJECT_SALE
    }
}
