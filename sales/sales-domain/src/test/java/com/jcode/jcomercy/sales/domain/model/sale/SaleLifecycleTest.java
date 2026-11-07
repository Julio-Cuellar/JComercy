package com.jcode.jcomercy.sales.domain.model.sale;

import com.jcode.jcomercy.sales.domain.exception.SalesDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.jcode.jcomercy.sales.domain.model.sale.SaleTestData.NOW;
import static com.jcode.jcomercy.sales.domain.model.sale.SaleTestData.cash;
import static com.jcode.jcomercy.sales.domain.model.sale.SaleTestData.pendingSale;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Los estados de una venta: pendiente, confirmando, completada y cancelada, y solo los pasos que tienen sentido. */
class SaleLifecycleTest {

    private static final Instant LATER = NOW.plusSeconds(30);

    private static Sale confirming() {
        return pendingSale().registerPayments(List.of(cash("101.00")));
    }

    @Test
    @DisplayName("Completar una venta que se está confirmando le pone su folio y su fecha")
    void completingSetsTheFolioAndDate() {
        Sale done = confirming().complete("TCK-000123", LATER);

        assertThat(done.status()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(done.folio()).isEqualTo("TCK-000123");
        assertThat(done.completedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("Una venta sin pagos no se puede completar")
    void aPendingSaleCannotBeCompleted() {
        assertThatThrownBy(() -> pendingSale().complete("TCK-000123", LATER))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("Solo se completa una venta con el pago registrado; esta está en PENDING.");
    }

    @Test
    @DisplayName("Completar pide un folio")
    void completingNeedsAFolio() {
        assertThatThrownBy(() -> confirming().complete(" ", LATER)).isInstanceOf(SalesDomainException.class);
        assertThatThrownBy(() -> confirming().complete(null, LATER)).isInstanceOf(SalesDomainException.class);
    }

    @Test
    @DisplayName("Una venta completada no se completa otra vez")
    void aCompletedSaleCannotBeCompletedAgain() {
        Sale done = confirming().complete("TCK-000123", LATER);

        assertThatThrownBy(() -> done.complete("TCK-000124", LATER)).isInstanceOf(SalesDomainException.class);
    }

    @Test
    @DisplayName("Una venta pendiente se puede cancelar con su motivo, y queda sin folio")
    void aPendingSaleCanBeCancelled() {
        Sale cancelled = pendingSale().cancel("Se acabó el stock", LATER);

        assertThat(cancelled.status()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(cancelled.cancelReason()).isEqualTo("Se acabó el stock");
        assertThat(cancelled.cancelledAt()).isEqualTo(LATER);
        assertThat(cancelled.folio()).isNull();
    }

    @Test
    @DisplayName("Una venta completada se puede cancelar y conserva su folio")
    void aCompletedSaleCanBeCancelled() {
        Sale cancelled = confirming().complete("TCK-000123", LATER).cancel("El cliente se arrepintió", LATER.plusSeconds(60));

        assertThat(cancelled.status()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(cancelled.folio()).isEqualTo("TCK-000123");
        assertThat(cancelled.completedAt()).isEqualTo(LATER);
        assertThat(cancelled.cancelledAt()).isEqualTo(LATER.plusSeconds(60));
    }

    @Test
    @DisplayName("Cancelar pide un motivo")
    void cancellingNeedsAReason() {
        assertThatThrownBy(() -> pendingSale().cancel(" ", LATER))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("Anota el motivo de la cancelación.");
        assertThatThrownBy(() -> pendingSale().cancel(null, LATER)).isInstanceOf(SalesDomainException.class);
    }

    @Test
    @DisplayName("Una venta cancelada no se cancela otra vez")
    void aCancelledSaleCannotBeCancelledAgain() {
        Sale cancelled = pendingSale().cancel("Error", LATER);

        assertThatThrownBy(() -> cancelled.cancel("Otra vez", LATER))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("La venta ya está cancelada.");
    }

    @Test
    @DisplayName("Una venta que se está confirmando no se cancela hasta que termine de confirmarse")
    void aConfirmingSaleCannotBeCancelled() {
        assertThatThrownBy(() -> confirming().cancel("Error", LATER))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("Esta venta se está confirmando. Espera a que termine para cancelarla.");
    }

    @Test
    @DisplayName("Las transiciones devuelven otra venta y no cambian la original")
    void transitionsDoNotChangeTheOriginal() {
        Sale confirming = confirming();

        confirming.complete("TCK-000123", LATER);

        assertThat(confirming.status()).isEqualTo(SaleStatus.CONFIRMING);
        assertThat(confirming.folio()).isNull();
    }
}
