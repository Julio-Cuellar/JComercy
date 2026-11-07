package com.jcode.jcomercy.sales.domain.model.sale;

import com.jcode.jcomercy.sales.domain.exception.SalesDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.jcode.jcomercy.sales.domain.model.sale.SaleTestData.NOW;
import static com.jcode.jcomercy.sales.domain.model.sale.SaleTestData.line;
import static com.jcode.jcomercy.sales.domain.model.sale.SaleTestData.pendingSale;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Una venta nueva: sus líneas, su total y lo que se exige para poder armarla. */
class SaleTest {

    @Test
    @DisplayName("Una venta nueva queda pendiente, sin folio ni pagos, y suma el total de sus líneas")
    void aNewSaleIsPendingAndTotalsItsLines() {
        Sale sale = pendingSale();

        assertThat(sale.status()).isEqualTo(SaleStatus.PENDING);
        assertThat(sale.total()).isEqualByComparingTo("101.00");
        assertThat(sale.folio()).isNull();
        assertThat(sale.payments()).isEmpty();
        assertThat(sale.received()).isEqualByComparingTo("0");
        assertThat(sale.change()).isEqualByComparingTo("0");
        assertThat(sale.channel()).isEqualTo(SaleChannel.MOSTRADOR);
        assertThat(sale.createdAt()).isEqualTo(NOW);
        assertThat(sale.completedAt()).isNull();
    }

    @Test
    @DisplayName("Las líneas se numeran desde 1 en el orden en que llegan")
    void linesAreNumberedFromOne() {
        Sale sale = pendingSale();

        assertThat(sale.lines()).extracting(SaleLine::lineNumber).containsExactly(1, 2);
        assertThat(sale.lines()).extracting(SaleLine::sku).containsExactly("CAFE", "GALLETA");
    }

    @Test
    @DisplayName("El importe de una línea se redondea a centavos, hacia arriba desde la mitad")
    void lineTotalsAreRoundedToCents() {
        // 0.5 kg a 33.33 son 16.665: se cobra 16.67.
        Sale sale = create(List.of(line("QUESO", "0.5", "33.33")));

        assertThat(sale.lines().get(0).lineTotal()).isEqualByComparingTo("16.67");
        assertThat(sale.total()).isEqualByComparingTo("16.67");
    }

    @Test
    @DisplayName("La moneda se guarda en mayúsculas y la referencia externa vacía se guarda como ausente")
    void currencyAndExternalReferenceAreNormalized() {
        Sale sale = Sale.create(UUID.randomUUID(), "cmp_1", "br_1", UUID.randomUUID(), SaleChannel.MOSTRADOR, "  ", "mxn",
            List.of(line("CAFE", "1", "10.00")), "usr_cajera", NOW);

        assertThat(sale.currency()).isEqualTo("MXN");
        assertThat(sale.externalReference()).isNull();
    }

    @Test
    @DisplayName("Una venta sin productos no se puede armar")
    void aSaleNeedsAtLeastOneProduct() {
        assertThatThrownBy(() -> create(List.of()))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("La venta necesita al menos un producto.");
    }

    @Test
    @DisplayName("La cantidad de cada producto debe ser mayor que cero")
    void quantityMustBePositive() {
        assertThatThrownBy(() -> create(List.of(line("CAFE", "0", "10.00"))))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("La cantidad de CAFE debe ser mayor que cero.");
        assertThatThrownBy(() -> create(List.of(line("CAFE", "-1", "10.00"))))
            .isInstanceOf(SalesDomainException.class);
    }

    @Test
    @DisplayName("El precio no puede ser negativo, pero puede ser cero")
    void priceCannotBeNegative() {
        assertThatThrownBy(() -> create(List.of(line("CAFE", "1", "-0.01"))))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("El precio de CAFE no puede ser negativo.");
        assertThat(create(List.of(line("MUESTRA", "1", "0.00"))).total()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("El mismo producto no puede repetirse en dos líneas")
    void aProductCannotRepeat() {
        UUID product = UUID.randomUUID();
        var first = SaleLine.of(product, "CAFE", "Café", new BigDecimal("1"), new BigDecimal("45.50"));
        var second = SaleLine.of(product, "CAFE", "Café", new BigDecimal("2"), new BigDecimal("45.50"));

        assertThatThrownBy(() -> create(List.of(first, second)))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("CAFE aparece en dos líneas. Junta las cantidades en una sola.");
    }

    @Test
    @DisplayName("La moneda debe ser un código de tres letras")
    void currencyMustBeAThreeLetterCode() {
        assertThatThrownBy(() -> Sale.create(UUID.randomUUID(), "cmp_1", "br_1", UUID.randomUUID(), SaleChannel.MOSTRADOR,
            null, "PESOS", List.of(line("CAFE", "1", "10.00")), "usr_cajera", NOW))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("La moneda debe ser un código de tres letras, por ejemplo MXN.");
    }

    @Test
    @DisplayName("Sin empresa, sucursal, almacén, canal o quien vende no hay venta")
    void theBasicDataIsRequired() {
        var lines = List.of(line("CAFE", "1", "10.00"));

        assertThatThrownBy(() -> Sale.create(UUID.randomUUID(), " ", "br_1", UUID.randomUUID(), SaleChannel.MOSTRADOR, null, "MXN", lines, "usr", NOW))
            .isInstanceOf(SalesDomainException.class);
        assertThatThrownBy(() -> Sale.create(UUID.randomUUID(), "cmp_1", " ", UUID.randomUUID(), SaleChannel.MOSTRADOR, null, "MXN", lines, "usr", NOW))
            .isInstanceOf(SalesDomainException.class);
        assertThatThrownBy(() -> Sale.create(UUID.randomUUID(), "cmp_1", "br_1", null, SaleChannel.MOSTRADOR, null, "MXN", lines, "usr", NOW))
            .isInstanceOf(SalesDomainException.class);
        assertThatThrownBy(() -> Sale.create(UUID.randomUUID(), "cmp_1", "br_1", UUID.randomUUID(), null, null, "MXN", lines, "usr", NOW))
            .isInstanceOf(SalesDomainException.class);
        assertThatThrownBy(() -> Sale.create(UUID.randomUUID(), "cmp_1", "br_1", UUID.randomUUID(), SaleChannel.MOSTRADOR, null, "MXN", lines, " ", NOW))
            .isInstanceOf(SalesDomainException.class);
    }

    private static Sale create(List<SaleLine> lines) {
        return Sale.create(UUID.randomUUID(), "cmp_1", "br_1", UUID.randomUUID(), SaleChannel.MOSTRADOR, null, "MXN", lines,
            "usr_cajera", NOW);
    }
}
