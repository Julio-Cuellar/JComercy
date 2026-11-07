package com.jcode.jcomercy.sales.application.service;

import com.jcode.jcomercy.sales.domain.exception.SalesDomainException;
import com.jcode.jcomercy.sales.domain.model.price.ProductPrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fijar y consultar el precio de venta de cada producto de una empresa. */
class PriceApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T18:00:00Z");

    private InMemorySalesPorts.Prices prices;
    private PriceApplicationService service;

    @BeforeEach
    void service() {
        prices = new InMemorySalesPorts.Prices();
        service = new PriceApplicationService(prices, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Fijar el precio de un producto lo guarda con la fecha y quien lo fijó")
    void settingAPriceStoresItWithTheDateAndTheActor() {
        UUID product = UUID.randomUUID();

        ProductPrice price = service.setPrice(new PriceApplicationService.SetPriceCommand(
            "cmp_1", product, new BigDecimal("45.5"), "mxn", "usr_gerente"));

        assertThat(price.amount().toPlainString()).isEqualTo("45.50");
        assertThat(price.currency()).isEqualTo("MXN");
        assertThat(price.updatedAt()).isEqualTo(NOW);
        assertThat(price.updatedBy()).isEqualTo("usr_gerente");
        assertThat(service.list("cmp_1")).containsExactly(price);
    }

    @Test
    @DisplayName("Fijarlo otra vez reemplaza el precio y no deja dos")
    void settingItAgainReplacesThePrice() {
        UUID product = UUID.randomUUID();
        service.setPrice(new PriceApplicationService.SetPriceCommand("cmp_1", product, new BigDecimal("45.50"), "MXN", "usr_1"));

        service.setPrice(new PriceApplicationService.SetPriceCommand("cmp_1", product, new BigDecimal("50.00"), "MXN", "usr_1"));

        assertThat(service.list("cmp_1")).hasSize(1);
        assertThat(service.list("cmp_1").get(0).amount()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("Cada empresa ve solo sus precios")
    void eachCompanySeesOnlyItsPrices() {
        UUID product = UUID.randomUUID();
        service.setPrice(new PriceApplicationService.SetPriceCommand("cmp_1", product, new BigDecimal("45.50"), "MXN", "usr_1"));
        service.setPrice(new PriceApplicationService.SetPriceCommand("cmp_2", product, new BigDecimal("99.00"), "MXN", "usr_2"));

        assertThat(service.list("cmp_1")).extracting(ProductPrice::amount).containsExactly(new BigDecimal("45.50"));
        assertThat(service.list("cmp_2")).extracting(ProductPrice::amount).containsExactly(new BigDecimal("99.00"));
        assertThat(service.list("cmp_3")).isEmpty();
    }

    @Test
    @DisplayName("Un precio inválido no se guarda")
    void anInvalidPriceIsNotStored() {
        assertThatThrownBy(() -> service.setPrice(new PriceApplicationService.SetPriceCommand(
            "cmp_1", UUID.randomUUID(), new BigDecimal("-1"), "MXN", "usr_1")))
            .isInstanceOf(SalesDomainException.class);

        assertThat(service.list("cmp_1")).isEmpty();
    }
}
