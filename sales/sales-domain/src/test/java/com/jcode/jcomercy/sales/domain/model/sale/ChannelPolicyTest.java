package com.jcode.jcomercy.sales.domain.model.sale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cada canal trae su política: de dónde sale el precio, quién registra el pago y qué pasa si falta stock. Hoy solo
 * existe el mostrador; un canal externo traerá la suya cuando se construya su conector.
 */
class ChannelPolicyTest {

    @Test
    @DisplayName("El mostrador cobra con la lista de precios, registra el pago en la venta y rechaza si falta stock")
    void theCounterPolicy() {
        ChannelPolicy policy = SaleChannel.MOSTRADOR.policy();

        assertThat(policy.priceSource()).isEqualTo(ChannelPolicy.PriceSource.CATALOG);
        assertThat(policy.paymentCollection()).isEqualTo(ChannelPolicy.PaymentCollection.REGISTERED_IN_SALE);
        assertThat(policy.stockShortage()).isEqualTo(ChannelPolicy.StockShortage.REJECT_SALE);
    }

    @Test
    @DisplayName("Hoy el mostrador es el único canal")
    void theCounterIsTheOnlyChannel() {
        assertThat(SaleChannel.values()).containsExactly(SaleChannel.MOSTRADOR);
    }
}
