package com.jcode.jcomercy.sales.application.service;

import com.jcode.jcomercy.sales.application.exception.InventoryUnavailableException;
import com.jcode.jcomercy.sales.application.exception.SalesApplicationException;
import com.jcode.jcomercy.sales.application.port.InventoryGatewayPort;
import com.jcode.jcomercy.sales.domain.exception.SalesDomainException;
import com.jcode.jcomercy.sales.domain.model.price.ProductPrice;
import com.jcode.jcomercy.sales.domain.model.sale.Payment;
import com.jcode.jcomercy.sales.domain.model.sale.PaymentMethod;
import com.jcode.jcomercy.sales.domain.model.sale.Sale;
import com.jcode.jcomercy.sales.domain.model.sale.SaleChannel;
import com.jcode.jcomercy.sales.domain.model.sale.SaleLine;
import com.jcode.jcomercy.sales.domain.model.sale.SaleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cancelar una venta: una pendiente libera su apartado, una completada devuelve su stock; una ya cancelada o que se está
 * confirmando se rechaza. Si inventario falla a medias, repetir la cancelación la termina sin devolver dos veces.
 */
class SaleCancellationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T18:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(600);
    private static final UUID CAFE = UUID.fromString("00000000-0000-0000-0000-00000000ca1e");
    private static final UUID GALLETA = UUID.fromString("00000000-0000-0000-0000-0000000009a1");
    private static final UUID WAREHOUSE = UUID.fromString("00000000-0000-0000-0000-00000000aaaa");
    private static final InventoryGatewayPort.Caller CALLER = new InventoryGatewayPort.Caller("Bearer token-del-gerente", "cor_test");

    private InMemorySalesPorts.Sales sales;
    private InMemorySalesPorts.Events events;
    private FakeInventory inventory;
    private SaleCheckoutService checkout;
    private SaleCancellationService service;

    @BeforeEach
    void service() {
        var prices = new InMemorySalesPorts.Prices();
        sales = new InMemorySalesPorts.Sales();
        events = new InMemorySalesPorts.Events();
        inventory = new FakeInventory();
        inventory.product(CAFE, "CAFE", "Café", "NONE", "ACTIVE", "10");
        inventory.product(GALLETA, "GALLETA", "Galleta", "NONE", "ACTIVE", "5");
        prices.save(new ProductPrice("cmp_1", CAFE, new BigDecimal("45.50"), "MXN", NOW, "usr_gerente"));
        prices.save(new ProductPrice("cmp_1", GALLETA, new BigDecimal("10.00"), "MXN", NOW, "usr_gerente"));
        Clock sellClock = Clock.fixed(NOW, ZoneOffset.UTC);
        checkout = new SaleCheckoutService(prices, sales, inventory,
            new SaleCompletionService(sales, new InMemorySalesPorts.Folios(), events, sellClock), sellClock, Duration.ofMinutes(15));
        service = new SaleCancellationService(sales, inventory,
            new SaleCancellationRecorder(sales, events, Clock.fixed(LATER, ZoneOffset.UTC)));
    }

    @Test
    @DisplayName("Cancelar una venta completada deja un evento de venta cancelada, con quien la canceló y el seguimiento")
    void cancellingACompletedSaleEmitsACancelledEvent() {
        Sale sale = completedSale("k-1");
        events.emitted.clear();

        Sale cancelled = service.cancel(cancel(sale, "El cliente devolvió todo"));

        assertThat(events.emitted).hasSize(1);
        var event = events.emitted.get(0);
        assertThat(event.type()).isEqualTo("cancelled");
        assertThat(event.sale()).isEqualTo(cancelled);
        assertThat(event.actorUserId()).isEqualTo("usr_gerente");
        assertThat(event.correlationId()).isEqualTo("cor_test");
    }

    @Test
    @DisplayName("Cancelar una venta pendiente no emite nada: nunca se completó")
    void cancellingAPendingSaleEmitsNothing() {
        service.cancel(cancel(pendingSale(), "Se abandonó"));

        assertThat(events.emitted).isEmpty();
    }

    @Test
    @DisplayName("Si la cancelación se rechaza o inventario falla a medias, no sale ningún evento hasta que termina")
    void noEventComesOutUntilTheCancellationFinishes() {
        Sale sale = completedSale("k-1");
        events.emitted.clear();
        inventory.failReturnsAfter(1);
        assertThatThrownBy(() -> service.cancel(cancel(sale, "x"))).isInstanceOf(InventoryUnavailableException.class);
        assertThat(events.emitted).isEmpty();

        inventory.healReturns();
        service.cancel(cancel(sale, "x"));

        assertThat(events.emitted).hasSize(1);
        assertThatThrownBy(() -> service.cancel(cancel(sale, "x"))).isInstanceOf(SalesApplicationException.class);
        assertThat(events.emitted).hasSize(1);
    }

    @Test
    @DisplayName("Cancelar una venta completada devuelve el stock de cada línea y la deja cancelada, con su folio y su motivo")
    void cancellingACompletedSaleReturnsTheStock() {
        Sale sale = completedSale("k-1");

        Sale cancelled = service.cancel(cancel(sale, "El cliente devolvió todo"));

        assertThat(cancelled.status()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(cancelled.cancelReason()).isEqualTo("El cliente devolvió todo");
        assertThat(cancelled.cancelledAt()).isEqualTo(LATER);
        assertThat(cancelled.folio()).isEqualTo(sale.folio());
        assertThat(inventory.effects.stream().filter(e -> e.startsWith("return")))
            .containsExactly("return " + sale.id() + ":1", "return " + sale.id() + ":2");
        assertThat(sales.findById("cmp_1", "br_1", sale.id())).contains(cancelled);
    }

    @Test
    @DisplayName("Cada devolución lleva una llave que sale de la venta y la línea, y el motivo de la cancelación")
    void eachReturnCarriesAKeyFromTheSaleAndTheReason() {
        Sale sale = completedSale("k-1");

        service.cancel(cancel(sale, "Se pasó el precio"));

        assertThat(inventory.calls).contains(
            "return " + sale.id() + ":1 key=sale-" + sale.id() + "-return-1 reason=Se pasó el precio",
            "return " + sale.id() + ":2 key=sale-" + sale.id() + "-return-2 reason=Se pasó el precio");
    }

    @Test
    @DisplayName("Cancelar una venta pendiente libera lo que tenía apartado")
    void cancellingAPendingSaleReleasesItsReservations() {
        Sale pending = pendingSale();

        Sale cancelled = service.cancel(cancel(pending, "Se abandonó"));

        assertThat(cancelled.status()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(inventory.effects).containsExactly("release " + pending.id() + ":1");
        assertThat(inventory.calls).contains("release " + pending.id() + ":1 key=sale-" + pending.id() + "-release-1");
    }

    @Test
    @DisplayName("Si el apartado de una venta pendiente ya venció, igual se cancela")
    void anExpiredReservationDoesNotBlockCancellingAPendingSale() {
        Sale pending = pendingSale();
        inventory.rejectReleases("no active reservation found for this reference");

        assertThat(service.cancel(cancel(pending, "Se abandonó")).status()).isEqualTo(SaleStatus.CANCELLED);
    }

    @Test
    @DisplayName("Una venta que ya está cancelada no se cancela otra vez, y no se toca inventario")
    void anAlreadyCancelledSaleIsRejected() {
        Sale sale = completedSale("k-1");
        service.cancel(cancel(sale, "Primera vez"));
        int callsBefore = inventory.calls.size();

        assertThatThrownBy(() -> service.cancel(cancel(sale, "Otra vez")))
            .isInstanceOf(SalesApplicationException.class)
            .hasMessage("La venta ya está cancelada.")
            .satisfies(e -> assertThat(((SalesApplicationException) e).kind()).isEqualTo(SalesApplicationException.Kind.CONFLICT));

        assertThat(inventory.calls).hasSize(callsBefore);
    }

    @Test
    @DisplayName("Una venta que se está confirmando no se cancela hasta que termine, y no se toca inventario")
    void aConfirmingSaleCannotBeCancelled() {
        inventory.failCommitsAfter(0);
        var pending = catchPending(() -> checkout.checkout(checkoutCommand("k-1")));
        int callsBefore = inventory.calls.size();

        assertThatThrownBy(() -> service.cancel(new SaleCancellationService.CancelCommand("cmp_1", "br_1", pending, "Error",
            "usr_gerente", CALLER)))
            .isInstanceOf(SalesApplicationException.class)
            .hasMessage("Esta venta se está confirmando. Espera a que termine para cancelarla.");

        assertThat(inventory.calls).hasSize(callsBefore);
    }

    @Test
    @DisplayName("Una venta de otra sucursal o de otra empresa no se encuentra")
    void aSaleOfAnotherBranchOrCompanyIsNotFound() {
        Sale sale = completedSale("k-1");

        for (var scope : List.of(List.of("cmp_1", "br_2"), List.of("cmp_2", "br_1"))) {
            assertThatThrownBy(() -> service.cancel(new SaleCancellationService.CancelCommand(scope.get(0), scope.get(1), sale.id(),
                "x", "usr_gerente", CALLER)))
                .isInstanceOf(SalesApplicationException.class)
                .hasMessage("No encontramos esa venta en esta sucursal.");
        }
        assertThat(inventory.effects.stream().filter(e -> e.startsWith("return"))).isEmpty();
    }

    @Test
    @DisplayName("Sin motivo no se cancela, y no se toca inventario")
    void aReasonIsRequiredBeforeTouchingInventory() {
        Sale sale = completedSale("k-1");
        int callsBefore = inventory.calls.size();

        assertThatThrownBy(() -> service.cancel(cancel(sale, " ")))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("Anota el motivo de la cancelación.");

        assertThat(inventory.calls).hasSize(callsBefore);
        assertThat(sales.findById("cmp_1", "br_1", sale.id()).orElseThrow().status()).isEqualTo(SaleStatus.COMPLETED);
    }

    @Test
    @DisplayName("Si inventario falla a medias, la venta sigue completada y repetir la cancelación la termina sin devolver dos veces")
    void aFailureHalfWayIsFinishedByRepeatingTheCancellation() {
        Sale sale = completedSale("k-1");
        inventory.failReturnsAfter(1);

        assertThatThrownBy(() -> service.cancel(cancel(sale, "El cliente devolvió todo")))
            .isInstanceOf(InventoryUnavailableException.class);
        assertThat(sales.findById("cmp_1", "br_1", sale.id()).orElseThrow().status()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(inventory.effects).contains("return " + sale.id() + ":1").doesNotContain("return " + sale.id() + ":2");

        inventory.healReturns();
        Sale cancelled = service.cancel(cancel(sale, "El cliente devolvió todo"));

        assertThat(cancelled.status()).isEqualTo(SaleStatus.CANCELLED);
        // La primera línea se pidió de nuevo con la misma llave y no se devolvió dos veces.
        assertThat(inventory.calls.stream().filter(c -> c.startsWith("return " + sale.id() + ":1 ")).count()).isEqualTo(2);
        assertThat(inventory.effects.stream().filter(("return " + sale.id() + ":1")::equals).count()).isEqualTo(1);
        assertThat(inventory.effects.stream().filter(("return " + sale.id() + ":2")::equals).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Si inventario dice que esa referencia ya se devolvió, se da por devuelta y la venta se cancela")
    void anAlreadyReturnedReferenceCountsAsDone() {
        Sale sale = completedSale("k-1");
        inventory.rejectReturns("this reference has already been returned");

        assertThat(service.cancel(cancel(sale, "Otra persona ya la devolvió")).status()).isEqualTo(SaleStatus.CANCELLED);
    }

    @Test
    @DisplayName("Si inventario rechaza devolver por otra razón, la venta sigue completada y se explica por qué")
    void anotherRejectionKeepsTheSaleCompleted() {
        Sale sale = completedSale("k-1");
        inventory.rejectReturns("no tienes permiso");

        assertThatThrownBy(() -> service.cancel(cancel(sale, "x")))
            .isInstanceOf(SalesApplicationException.class)
            .hasMessage("Inventario no pudo devolver el stock de CAFE: no tienes permiso. La venta sigue completada.");

        assertThat(sales.findById("cmp_1", "br_1", sale.id()).orElseThrow().status()).isEqualTo(SaleStatus.COMPLETED);
    }

    // ---- Ayudas ----

    private SaleCancellationService.CancelCommand cancel(Sale sale, String reason) {
        return new SaleCancellationService.CancelCommand(sale.companyId(), sale.branchId(), sale.id(), reason, "usr_gerente", CALLER);
    }

    private SaleCheckoutService.CheckoutCommand checkoutCommand(String key) {
        return new SaleCheckoutService.CheckoutCommand("cmp_1", "br_1", WAREHOUSE,
            List.of(new SaleCheckoutService.LineInput(CAFE, new BigDecimal("2")), new SaleCheckoutService.LineInput(GALLETA, BigDecimal.ONE)),
            List.of(new Payment(PaymentMethod.CASH, new BigDecimal("101.00"), null)), "usr_cajera", key,
            new InventoryGatewayPort.Caller("Bearer token-de-la-cajera", "cor_test"));
    }

    private Sale completedSale(String key) {
        return checkout.checkout(checkoutCommand(key)).sale();
    }

    private Sale pendingSale() {
        Sale pending = Sale.create(UUID.randomUUID(), "cmp_1", "br_1", WAREHOUSE, SaleChannel.MOSTRADOR, null, "MXN",
            List.of(SaleLine.of(CAFE, "CAFE", "Café", new BigDecimal("2"), new BigDecimal("45.50"))), "usr_cajera", NOW);
        return sales.save(pending);
    }

    private UUID catchPending(Runnable action) {
        try {
            action.run();
        } catch (com.jcode.jcomercy.sales.application.exception.SaleConfirmationPendingException pending) {
            return pending.saleId();
        }
        throw new AssertionError("Se esperaba una venta pendiente de confirmar");
    }
}
