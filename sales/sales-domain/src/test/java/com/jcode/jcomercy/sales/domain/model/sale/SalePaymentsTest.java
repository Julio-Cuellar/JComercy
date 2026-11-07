package com.jcode.jcomercy.sales.domain.model.sale;

import com.jcode.jcomercy.sales.domain.exception.SalesDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.jcode.jcomercy.sales.domain.model.sale.SaleTestData.card;
import static com.jcode.jcomercy.sales.domain.model.sale.SaleTestData.cash;
import static com.jcode.jcomercy.sales.domain.model.sale.SaleTestData.pendingSale;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El cobro de una venta de 101.00: el efectivo da cambio, la tarjeta y la transferencia deben ser exactas, y nunca se
 * guarda el número de una tarjeta.
 */
class SalePaymentsTest {

    @Test
    @DisplayName("Pagar exacto en efectivo deja la venta lista para confirmar, sin cambio")
    void exactCashLeavesNoChange() {
        Sale paid = pendingSale().registerPayments(List.of(cash("101.00")));

        assertThat(paid.status()).isEqualTo(SaleStatus.CONFIRMING);
        assertThat(paid.received()).isEqualByComparingTo("101.00");
        assertThat(paid.change()).isEqualByComparingTo("0");
        assertThat(paid.payments()).hasSize(1);
    }

    @Test
    @DisplayName("Pagar de más en efectivo devuelve cambio")
    void overpayingInCashGivesChange() {
        Sale paid = pendingSale().registerPayments(List.of(cash("200.00")));

        assertThat(paid.received()).isEqualByComparingTo("200.00");
        assertThat(paid.change()).isEqualByComparingTo("99.00");
    }

    @Test
    @DisplayName("Una tarjeta por el total exacto se acepta")
    void aCardForTheExactTotalIsAccepted() {
        Sale paid = pendingSale().registerPayments(List.of(card("101.00")));

        assertThat(paid.status()).isEqualTo(SaleStatus.CONFIRMING);
        assertThat(paid.change()).isEqualByComparingTo("0");
        assertThat(paid.payments().get(0).reference()).isEqualTo("AUT-123456");
    }

    @Test
    @DisplayName("Se puede pagar parte con tarjeta y el resto en efectivo, y el cambio sale del efectivo")
    void splitPaymentGivesChangeFromTheCash() {
        Sale paid = pendingSale().registerPayments(List.of(card("50.00"), cash("60.00")));

        assertThat(paid.received()).isEqualByComparingTo("110.00");
        assertThat(paid.change()).isEqualByComparingTo("9.00");
    }

    @Test
    @DisplayName("La tarjeta o la transferencia no pueden pasar del total: no hay cambio en tarjeta")
    void cardsAndTransfersCannotExceedTheTotal() {
        assertThatThrownBy(() -> pendingSale().registerPayments(List.of(card("150.00"))))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("La tarjeta y la transferencia no pueden pasar del total de 101.00. Cobra el excedente en efectivo.");
        assertThatThrownBy(() -> pendingSale().registerPayments(List.of(
                new Payment(PaymentMethod.TRANSFER, new BigDecimal("60.00"), null), card("60.00"))))
            .isInstanceOf(SalesDomainException.class);
    }

    @Test
    @DisplayName("Si lo recibido no cubre el total, se dice cuánto falta")
    void ifThePaymentsDoNotCoverTheTotalItSaysHowMuchIsMissing() {
        assertThatThrownBy(() -> pendingSale().registerPayments(List.of(cash("90.00"))))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("Faltan 11.00 para cubrir el total de 101.00.");
    }

    @Test
    @DisplayName("Sin pagos, o con un pago de cero o negativo, no se cobra")
    void paymentsMustExistAndBePositive() {
        assertThatThrownBy(() -> pendingSale().registerPayments(List.of()))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("Registra al menos un pago.");
        assertThatThrownBy(() -> pendingSale().registerPayments(List.of(cash("0.00"), cash("101.00"))))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("Cada pago debe ser mayor que cero.");
        assertThatThrownBy(() -> pendingSale().registerPayments(List.of(cash("-5.00"), cash("106.00"))))
            .isInstanceOf(SalesDomainException.class);
    }

    @Test
    @DisplayName("Nunca se guarda el número de una tarjeta: una referencia con 13 dígitos o más se rechaza")
    void aReferenceThatLooksLikeACardNumberIsRejected() {
        for (String reference : List.of("4111111111111111", "4111 1111 1111 1111", "4111-1111-1111-1111", "REF 4111111111111")) {
            var payment = new Payment(PaymentMethod.CARD, new BigDecimal("101.00"), reference);

            assertThatThrownBy(() -> pendingSale().registerPayments(List.of(payment)))
                .as(reference)
                .isInstanceOf(SalesDomainException.class)
                .hasMessage("La referencia del pago parece un número de tarjeta. Anota solo la autorización, nunca el número de la tarjeta.");
        }
    }

    @Test
    @DisplayName("Una referencia de autorización normal sí se acepta")
    void anAuthorizationReferenceIsAccepted() {
        for (String reference : List.of("AUT-123456", "A1B2C3", "123456789012")) {
            var payment = new Payment(PaymentMethod.CARD, new BigDecimal("101.00"), reference);

            assertThat(pendingSale().registerPayments(List.of(payment)).status()).as(reference).isEqualTo(SaleStatus.CONFIRMING);
        }
    }

    @Test
    @DisplayName("Los pagos solo se registran una vez, mientras la venta está pendiente")
    void paymentsAreRegisteredOnlyOnce() {
        Sale paid = pendingSale().registerPayments(List.of(cash("101.00")));

        assertThatThrownBy(() -> paid.registerPayments(List.of(cash("101.00"))))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("Esta venta ya no admite pagos: está en CONFIRMING.");
    }

    @Test
    @DisplayName("Registrar los pagos no cambia la venta original")
    void registeringPaymentsLeavesTheOriginalUntouched() {
        Sale pending = pendingSale();

        pending.registerPayments(List.of(cash("101.00")));

        assertThat(pending.status()).isEqualTo(SaleStatus.PENDING);
        assertThat(pending.payments()).isEmpty();
    }
}
