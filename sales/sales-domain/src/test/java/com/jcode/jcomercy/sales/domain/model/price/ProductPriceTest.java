package com.jcode.jcomercy.sales.domain.model.price;

import com.jcode.jcomercy.sales.domain.exception.SalesDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El precio de venta de un producto: final, con IVA incluido, en centavos y en una moneda de tres letras. */
class ProductPriceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T18:00:00Z");

    @Test
    @DisplayName("El precio se guarda en centavos y la moneda en mayúsculas")
    void theAmountIsKeptInCentsAndTheCurrencyInUpperCase() {
        ProductPrice price = new ProductPrice("cmp_1", UUID.randomUUID(), new BigDecimal("45.5"), "mxn", NOW, "usr_1");

        assertThat(price.amount().toPlainString()).isEqualTo("45.50");
        assertThat(price.currency()).isEqualTo("MXN");
    }

    @Test
    @DisplayName("El precio no puede ser negativo, pero puede ser cero")
    void thePriceCannotBeNegative() {
        assertThatThrownBy(() -> new ProductPrice("cmp_1", UUID.randomUUID(), new BigDecimal("-0.01"), "MXN", NOW, "usr_1"))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("El precio no puede ser negativo.");
        assertThat(new ProductPrice("cmp_1", UUID.randomUUID(), BigDecimal.ZERO, "MXN", NOW, "usr_1").amount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("La moneda debe ser un código de tres letras")
    void theCurrencyMustBeAThreeLetterCode() {
        assertThatThrownBy(() -> new ProductPrice("cmp_1", UUID.randomUUID(), BigDecimal.ONE, "PESOS", NOW, "usr_1"))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("La moneda debe ser un código de tres letras, por ejemplo MXN.");
    }

    @Test
    @DisplayName("Sin empresa, producto o quien lo fija no hay precio")
    void theBasicDataIsRequired() {
        assertThatThrownBy(() -> new ProductPrice(" ", UUID.randomUUID(), BigDecimal.ONE, "MXN", NOW, "usr_1"))
            .isInstanceOf(SalesDomainException.class);
        assertThatThrownBy(() -> new ProductPrice("cmp_1", null, BigDecimal.ONE, "MXN", NOW, "usr_1"))
            .isInstanceOf(SalesDomainException.class);
        assertThatThrownBy(() -> new ProductPrice("cmp_1", UUID.randomUUID(), null, "MXN", NOW, "usr_1"))
            .isInstanceOf(SalesDomainException.class);
        assertThatThrownBy(() -> new ProductPrice("cmp_1", UUID.randomUUID(), BigDecimal.ONE, "MXN", NOW, " "))
            .isInstanceOf(SalesDomainException.class);
    }
}
