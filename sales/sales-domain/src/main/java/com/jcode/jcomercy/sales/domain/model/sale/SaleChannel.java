package com.jcode.jcomercy.sales.domain.model.sale;

/** El canal por el que entra una venta. Hoy solo el mostrador; cada canal nuevo trae su política. */
public enum SaleChannel {
    MOSTRADOR(new ChannelPolicy(ChannelPolicy.PriceSource.CATALOG, ChannelPolicy.PaymentCollection.REGISTERED_IN_SALE,
            ChannelPolicy.StockShortage.REJECT_SALE));

    private final ChannelPolicy policy;

    SaleChannel(ChannelPolicy policy) {
        this.policy = policy;
    }

    public ChannelPolicy policy() {
        return policy;
    }
}
