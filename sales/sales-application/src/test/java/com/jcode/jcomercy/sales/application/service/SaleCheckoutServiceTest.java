package com.jcode.jcomercy.sales.application.service;

import com.jcode.jcomercy.sales.application.exception.InventoryUnavailableException;
import com.jcode.jcomercy.sales.application.exception.SalesApplicationException;
import com.jcode.jcomercy.sales.application.exception.SaleConfirmationPendingException;
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
 * Cobrar una venta de mostrador: precio de la lista, apartado y confirmación en inventario, folio al completar, y qué
 * pasa cuando algo falla. Repetir la petición con la misma llave retoma la venta donde se quedó y nunca duplica.
 */
class SaleCheckoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T18:00:00Z");
    private static final UUID CAFE = UUID.fromString("00000000-0000-0000-0000-00000000ca1e");
    private static final UUID GALLETA = UUID.fromString("00000000-0000-0000-0000-0000000009a1");
    private static final UUID AGUA = UUID.fromString("00000000-0000-0000-0000-0000000000a9");
    private static final UUID WAREHOUSE = UUID.fromString("00000000-0000-0000-0000-00000000aaaa");
    private static final InventoryGatewayPort.Caller CALLER = new InventoryGatewayPort.Caller("Bearer token-de-la-cajera", "cor_test");

    private InMemorySalesPorts.Prices prices;
    private InMemorySalesPorts.Sales sales;
    private InMemorySalesPorts.Folios folios;
    private FakeInventory inventory;
    private SaleCheckoutService service;

    @BeforeEach
    void service() {
        prices = new InMemorySalesPorts.Prices();
        sales = new InMemorySalesPorts.Sales();
        folios = new InMemorySalesPorts.Folios();
        inventory = new FakeInventory();
        inventory.product(CAFE, "CAFE", "Café", "NONE", "ACTIVE", "10");
        inventory.product(GALLETA, "GALLETA", "Galleta", "NONE", "ACTIVE", "5");
        inventory.product(AGUA, "AGUA", "Agua", "NONE", "ACTIVE", "5");
        prices.save(new ProductPrice("cmp_1", CAFE, new BigDecimal("45.50"), "MXN", NOW, "usr_gerente"));
        prices.save(new ProductPrice("cmp_1", GALLETA, new BigDecimal("10.00"), "MXN", NOW, "usr_gerente"));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new SaleCheckoutService(prices, sales, inventory, new SaleCompletionService(sales, folios, clock), clock,
            Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("Una venta se cobra de punta a punta: precio de la lista, stock apartado y confirmado, y folio al final")
    void aSaleIsChargedFromStartToFinish() {
        var result = service.checkout(command("k-1", List.of(line(CAFE, "2"), line(GALLETA, "1")), List.of(cash("101.00"))));

        Sale sale = result.sale();
        assertThat(result.replayed()).isFalse();
        assertThat(sale.status()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(sale.folio()).isEqualTo("TCK-000001");
        assertThat(sale.total()).isEqualByComparingTo("101.00");
        assertThat(sale.channel()).isEqualTo(SaleChannel.MOSTRADOR);
        assertThat(sale.warehouseId()).isEqualTo(WAREHOUSE);
        assertThat(sale.actorUserId()).isEqualTo("usr_cajera");
        assertThat(sale.lines()).extracting(SaleLine::sku).containsExactly("CAFE", "GALLETA");
        assertThat(sale.lines()).extracting(SaleLine::name).containsExactly("Café", "Galleta");
        assertThat(sale.lines()).extracting(SaleLine::unitPrice).usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
            .containsExactly(new BigDecimal("45.50"), new BigDecimal("10.00"));
        assertThat(sale.payments()).hasSize(1);
        assertThat(inventory.effects).containsExactly("reserve " + sale.id() + ":1", "reserve " + sale.id() + ":2",
            "commit " + sale.id() + ":1", "commit " + sale.id() + ":2");
        assertThat(sales.findById("cmp_1", "br_1", sale.id())).contains(sale);
    }

    @Test
    @DisplayName("El apartado va con la unidad del producto, la referencia de la venta y un plazo que sale de la fecha de la venta")
    void theReservationCarriesTheUnitTheReferenceAndADeadlineFromTheSaleDate() {
        var sale = service.checkout(command("k-1", List.of(line(CAFE, "2")), List.of(cash("91.00")))).sale();

        var reservation = inventory.reservations.get(0);
        assertThat(reservation.companyId()).isEqualTo("cmp_1");
        assertThat(reservation.branchId()).isEqualTo("br_1");
        assertThat(reservation.warehouseId()).isEqualTo(WAREHOUSE);
        assertThat(reservation.productId()).isEqualTo(CAFE);
        assertThat(reservation.unitCode()).isEqualTo("PZA");
        assertThat(reservation.referenceType()).isEqualTo("SALE");
        assertThat(reservation.referenceId()).isEqualTo(sale.id() + ":1");
        assertThat(reservation.quantity()).isEqualByComparingTo("2");
        assertThat(reservation.expiresAt()).isEqualTo(sale.createdAt().plus(Duration.ofMinutes(15)));
        assertThat(reservation.idempotencyKey()).isEqualTo("sale-" + sale.id() + "-reserve-1");
    }

    @Test
    @DisplayName("Un producto sin precio no se vende, y no se toca el inventario")
    void aProductWithoutAPriceIsNotSold() {
        assertThatThrownBy(() -> service.checkout(command("k-1", List.of(line(CAFE, "1"), line(AGUA, "1")), List.of(cash("100.00")))))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("Fija el precio de AGUA antes de venderlo.");

        assertThat(inventory.effects).isEmpty();
        assertThat(sales.findByBranch("cmp_1", "br_1")).isEmpty();
    }

    @Test
    @DisplayName("Los productos con lote o serie todavía no se venden en el mostrador")
    void trackedProductsAreNotSoldYet() {
        inventory.product(CAFE, "CAFE", "Café", "LOT", "ACTIVE", "10");

        assertThatThrownBy(() -> service.checkout(command("k-1", List.of(line(CAFE, "1")), List.of(cash("50.00")))))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("CAFE se vende por lote o serie y todavía no se puede vender en el mostrador.");

        assertThat(inventory.effects).isEmpty();
    }

    @Test
    @DisplayName("Un producto que no está activo en inventario no se vende")
    void anInactiveProductIsNotSold() {
        inventory.product(CAFE, "CAFE", "Café", "NONE", "INACTIVE", "10");

        assertThatThrownBy(() -> service.checkout(command("k-1", List.of(line(CAFE, "1")), List.of(cash("50.00")))))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("CAFE no está activo en inventario.");
    }

    @Test
    @DisplayName("Un producto que inventario no conoce responde que no se encontró")
    void anUnknownProductIsNotFound() {
        assertThatThrownBy(() -> service.checkout(command("k-1", List.of(line(UUID.randomUUID(), "1")), List.of(cash("50.00")))))
            .isInstanceOf(SalesApplicationException.class)
            .hasMessage("No encontramos uno de los productos en inventario.")
            .satisfies(e -> assertThat(((SalesApplicationException) e).kind()).isEqualTo(SalesApplicationException.Kind.NOT_FOUND));
    }

    @Test
    @DisplayName("Si no alcanza el stock de una línea, se libera lo apartado, la venta queda cancelada y no se gasta un folio")
    void insufficientStockReleasesWhatWasReservedAndCancelsTheSale() {
        assertThatThrownBy(() -> service.checkout(command("k-1", List.of(line(CAFE, "2"), line(GALLETA, "9")), List.of(cash("500.00")))))
            .isInstanceOf(SalesApplicationException.class)
            .hasMessage("No alcanza el stock de GALLETA. Revisa las existencias o baja la cantidad.")
            .satisfies(e -> assertThat(((SalesApplicationException) e).kind()).isEqualTo(SalesApplicationException.Kind.CONFLICT));

        Sale cancelled = sales.findByBranch("cmp_1", "br_1").get(0);
        assertThat(cancelled.status()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(cancelled.cancelReason()).contains("GALLETA");
        assertThat(cancelled.folio()).isNull();
        assertThat(inventory.effects).containsExactly("reserve " + cancelled.id() + ":1", "release " + cancelled.id() + ":1");

        // La siguiente venta de verdad recibe el primer folio: la fallida no gastó ninguno.
        assertThat(service.checkout(command("k-2", List.of(line(CAFE, "1")), List.of(cash("50.00")))).sale().folio()).isEqualTo("TCK-000001");
    }

    @Test
    @DisplayName("Repetir la llave de una venta que se canceló responde que ya se canceló")
    void repeatingTheKeyOfACancelledSaleReportsIt() {
        assertThatThrownBy(() -> service.checkout(command("k-1", List.of(line(GALLETA, "9")), List.of(cash("500.00")))))
            .isInstanceOf(SalesApplicationException.class);

        assertThatThrownBy(() -> service.checkout(command("k-1", List.of(line(GALLETA, "1")), List.of(cash("50.00")))))
            .isInstanceOf(SalesApplicationException.class)
            .hasMessage("Esa venta ya se canceló. Para vender de nuevo usa otra llave.");
    }

    @Test
    @DisplayName("Si inventario rechaza apartar, la venta queda cancelada con el motivo de inventario")
    void aRejectedReservationCancelsTheSale() {
        inventory.rejectReservations("no tienes permiso");

        assertThatThrownBy(() -> service.checkout(command("k-1", List.of(line(CAFE, "1")), List.of(cash("50.00")))))
            .isInstanceOf(SalesApplicationException.class)
            .hasMessage("Inventario no permitió apartar CAFE: no tienes permiso");

        assertThat(sales.findByBranch("cmp_1", "br_1").get(0).status()).isEqualTo(SaleStatus.CANCELLED);
    }

    @Test
    @DisplayName("Un pago que no cubre el total se rechaza antes de tocar el inventario, sin dejar nada guardado")
    void invalidPaymentsAreRejectedBeforeTouchingInventory() {
        assertThatThrownBy(() -> service.checkout(command("k-1", List.of(line(CAFE, "2")), List.of(cash("50.00")))))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("Faltan 41.00 para cubrir el total de 91.00.");

        assertThat(inventory.calls).noneMatch(call -> call.startsWith("reserve"));
        assertThat(sales.findByBranch("cmp_1", "br_1")).isEmpty();
    }

    @Test
    @DisplayName("Productos con monedas distintas no se mezclan en una venta")
    void productsInDifferentCurrenciesDoNotMix() {
        prices.save(new ProductPrice("cmp_1", GALLETA, new BigDecimal("1.00"), "USD", NOW, "usr_gerente"));

        assertThatThrownBy(() -> service.checkout(command("k-1", List.of(line(CAFE, "1"), line(GALLETA, "1")), List.of(cash("100.00")))))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("Los productos de esta venta tienen precios en monedas distintas. Corrige los precios antes de vender.");
    }

    @Test
    @DisplayName("El mismo producto en dos líneas se rechaza antes de apartar nada")
    void theSameProductTwiceIsRejectedBeforeReserving() {
        assertThatThrownBy(() -> service.checkout(command("k-1", List.of(line(CAFE, "1"), line(CAFE, "2")), List.of(cash("200.00")))))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("CAFE aparece en dos líneas. Junta las cantidades en una sola.");

        assertThat(inventory.calls).noneMatch(call -> call.startsWith("reserve"));
    }

    @Test
    @DisplayName("Si inventario no responde al buscar un producto, no se guarda nada")
    void anUnavailableInventoryLeavesNothingBehind() {
        inventory.unavailable(true);

        assertThatThrownBy(() -> service.checkout(command("k-1", List.of(line(CAFE, "1")), List.of(cash("50.00")))))
            .isInstanceOf(InventoryUnavailableException.class);

        assertThat(sales.findByBranch("cmp_1", "br_1")).isEmpty();
    }

    @Test
    @DisplayName("Si falla una confirmación, la venta queda confirmándose y repetir la petición la termina sin duplicar nada")
    void aFailedConfirmationCanBeFinishedByRepeatingTheRequest() {
        inventory.failCommitsAfter(1);
        var command = command("k-1", List.of(line(CAFE, "2"), line(GALLETA, "1")), List.of(cash("101.00")));

        var pending = catchThrowableOfType(SaleConfirmationPendingException.class, () -> service.checkout(command));

        Sale confirming = sales.findById("cmp_1", "br_1", pending.saleId()).orElseThrow();
        assertThat(confirming.status()).isEqualTo(SaleStatus.CONFIRMING);
        assertThat(confirming.payments()).hasSize(1);
        assertThat(confirming.folio()).isNull();
        assertThat(inventory.effects).contains("commit " + confirming.id() + ":1").doesNotContain("commit " + confirming.id() + ":2");

        inventory.healCommits();
        var finished = service.checkout(command);

        assertThat(finished.sale().id()).isEqualTo(confirming.id());
        assertThat(finished.sale().status()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(finished.sale().folio()).isEqualTo("TCK-000001");
        // El primer commit se repite con la misma llave y no se ejecuta dos veces; el segundo se ejecuta una vez.
        String firstCommit = "commit " + confirming.id() + ":1 key=sale-" + confirming.id() + "-commit-1";
        assertThat(inventory.calls.stream().filter(firstCommit::equals).count()).isEqualTo(2);
        assertThat(inventory.effects.stream().filter(("commit " + confirming.id() + ":1")::equals).count()).isEqualTo(1);
        assertThat(inventory.effects.stream().filter(("commit " + confirming.id() + ":2")::equals).count()).isEqualTo(1);
        assertThat(inventory.effects.stream().filter(e -> e.startsWith("reserve")).count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Repetir la llave de una venta ya cobrada devuelve la misma venta sin tocar inventario ni gastar otro folio")
    void repeatingTheKeyOfAChargedSaleReturnsTheSameSale() {
        var command = command("k-1", List.of(line(CAFE, "2")), List.of(cash("91.00")));
        var first = service.checkout(command);
        int callsBefore = inventory.calls.size();

        var again = service.checkout(command);

        assertThat(again.replayed()).isTrue();
        assertThat(again.sale()).isEqualTo(first.sale());
        assertThat(inventory.calls).hasSize(callsBefore);
        assertThat(service.checkout(command("k-2", List.of(line(CAFE, "1")), List.of(cash("50.00")))).sale().folio()).isEqualTo("TCK-000002");
    }

    @Test
    @DisplayName("Una venta pendiente que quedó guardada sin apartar se retoma con la misma llave")
    void aPendingSaleLeftBehindIsResumed() {
        Sale pending = Sale.create(UUID.randomUUID(), "cmp_1", "br_1", WAREHOUSE, SaleChannel.MOSTRADOR, null, "MXN",
            List.of(SaleLine.of(CAFE, "CAFE", "Café", new BigDecimal("2"), new BigDecimal("45.50"))), "usr_cajera", NOW);
        sales.create(pending, "k-9");

        var result = service.checkout(command("k-9", List.of(line(CAFE, "2")), List.of(cash("91.00"))));

        assertThat(result.sale().id()).isEqualTo(pending.id());
        assertThat(result.sale().status()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(inventory.effects).containsExactly("reserve " + pending.id() + ":1", "commit " + pending.id() + ":1");
    }

    @Test
    @DisplayName("Otra persona no puede retomar ni leer una venta con la llave de otra")
    void anotherPersonCannotReuseTheKeyOfSomeoneElsesSale() {
        service.checkout(command("k-1", List.of(line(CAFE, "1")), List.of(cash("50.00"))));
        var other = new SaleCheckoutService.CheckoutCommand("cmp_1", "br_1", WAREHOUSE, List.of(line(CAFE, "1")),
            List.of(cash("50.00")), "usr_otra", "k-1", CALLER);

        assertThatThrownBy(() -> service.checkout(other))
            .isInstanceOf(SalesApplicationException.class)
            .hasMessage("Esa llave ya se usó en otra venta. Usa una llave nueva.");
    }

    @Test
    @DisplayName("Una llave de idempotencia de más de 100 caracteres se rechaza con un mensaje claro")
    void aTooLongIdempotencyKeyIsRejected() {
        assertThatThrownBy(() -> service.checkout(command("k".repeat(101), List.of(line(CAFE, "1")), List.of(cash("50.00")))))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("La llave de idempotencia no puede pasar de 100 caracteres.");
        assertThat(service.checkout(command("k".repeat(100), List.of(line(CAFE, "1")), List.of(cash("50.00")))).sale().status())
            .isEqualTo(SaleStatus.COMPLETED);
    }

    @Test
    @DisplayName("Sin llave de idempotencia no se cobra")
    void aSaleNeedsAnIdempotencyKey() {
        assertThatThrownBy(() -> service.checkout(command(" ", List.of(line(CAFE, "1")), List.of(cash("50.00")))))
            .isInstanceOf(SalesDomainException.class)
            .hasMessage("Falta la llave de idempotencia de la venta.");
    }

    // ---- Ayudas ----

    private static SaleCheckoutService.CheckoutCommand command(String key, List<SaleCheckoutService.LineInput> lines, List<Payment> payments) {
        return new SaleCheckoutService.CheckoutCommand("cmp_1", "br_1", WAREHOUSE, lines, payments, "usr_cajera", key, CALLER);
    }

    private static SaleCheckoutService.LineInput line(UUID product, String quantity) {
        return new SaleCheckoutService.LineInput(product, new BigDecimal(quantity));
    }

    private static Payment cash(String amount) {
        return new Payment(PaymentMethod.CASH, new BigDecimal(amount), null);
    }

    private static <T extends Throwable> T catchThrowableOfType(Class<T> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (type.isInstance(thrown)) {
                return type.cast(thrown);
            }
            throw new AssertionError("Se esperaba " + type.getSimpleName() + " y llegó " + thrown, thrown);
        }
        throw new AssertionError("Se esperaba " + type.getSimpleName() + " y no se lanzó nada");
    }
}
