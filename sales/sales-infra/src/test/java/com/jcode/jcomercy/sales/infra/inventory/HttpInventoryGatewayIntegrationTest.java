package com.jcode.jcomercy.sales.infra.inventory;

import com.jcode.jcomercy.sales.application.exception.InsufficientStockException;
import com.jcode.jcomercy.sales.application.exception.InventoryRejectedException;
import com.jcode.jcomercy.sales.application.exception.InventoryUnavailableException;
import com.jcode.jcomercy.sales.application.port.InventoryGatewayPort.Caller;
import com.jcode.jcomercy.sales.application.port.InventoryGatewayPort.ProductInfo;
import com.jcode.jcomercy.sales.application.port.InventoryGatewayPort.StockReference;
import com.jcode.jcomercy.sales.application.port.InventoryGatewayPort.StockReservation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El adaptador que habla con el inventario real por HTTP, probado contra un inventario falso que se comporta como el
 * real: reenvía el token de quien vende, manda las llaves de idempotencia, escribe cuerpos estables y traduce cada falla
 * a la excepción que el cobro sabe manejar.
 */
class HttpInventoryGatewayIntegrationTest {

    private static final Caller CALLER = new Caller("Bearer token-de-la-cajera", "cor_test_1");

    private static FakeInventoryServer inventory;
    private HttpInventoryGateway gateway;

    @BeforeAll
    static void startInventory() throws IOException {
        inventory = FakeInventoryServer.start();
    }

    @AfterAll
    static void stopInventory() {
        inventory.close();
    }

    @BeforeEach
    void freshGateway() {
        inventory.failAllWith(0);
        inventory.healPaths();
        inventory.delay(0);
        inventory.requests.clear();
        gateway = new HttpInventoryGateway(inventory.baseUrl(), Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("Un producto se lee con el token de quien vende y se traduce a lo que Ventas necesita")
    void aProductIsReadWithTheCallerTokenAndTranslated() {
        UUID id = UUID.randomUUID();
        inventory.product(id, "cmp_1", "CAFE", "Café", "10");

        ProductInfo info = gateway.findProduct(CALLER, id).orElseThrow();

        assertThat(info).isEqualTo(new ProductInfo(id, "cmp_1", "CAFE", "Café", "PZA", "NONE", "ACTIVE"));
        var request = inventory.requestsTo("/api/v1/products/").get(0);
        assertThat(request.headers()).containsEntry("authorization", "Bearer token-de-la-cajera")
            .containsEntry("x-correlation-id", "cor_test_1");
    }

    @Test
    @DisplayName("Un producto que inventario no conoce da vacío; sin permiso, rechazado; con un error del servidor, no disponible")
    void productLookupFailuresAreTranslated() {
        assertThat(gateway.findProduct(CALLER, UUID.randomUUID())).isEmpty();

        inventory.failPath("/api/v1/products/", 403);
        assertThatThrownBy(() -> gateway.findProduct(CALLER, UUID.randomUUID())).isInstanceOf(InventoryRejectedException.class);

        inventory.failPath("/api/v1/products/", 500);
        assertThatThrownBy(() -> gateway.findProduct(CALLER, UUID.randomUUID())).isInstanceOf(InventoryUnavailableException.class);
    }

    @Test
    @DisplayName("Apartar manda la referencia, la unidad, el plazo, la llave y el token, y descuenta lo disponible")
    void reservingSendsTheReferenceUnitDeadlineKeyAndToken() {
        UUID product = UUID.randomUUID();
        inventory.product(product, "cmp_1", "CAFE", "Café", "10");

        gateway.reserve(CALLER, reservation(product, "2", "S-1:1", "llave-reserva-1"));

        var request = inventory.requestsTo("/stock/reservations").get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/api/v1/companies/cmp_1/branches/br_1/stock/reservations");
        assertThat(request.headers()).containsEntry("authorization", "Bearer token-de-la-cajera")
            .containsEntry("idempotency-key", "llave-reserva-1").containsEntry("x-correlation-id", "cor_test_1");
        assertThat(request.body()).contains("\"productId\":\"" + product + "\"").contains("\"unitCode\":\"PZA\"")
            .contains("\"referenceType\":\"SALE\"").contains("\"referenceId\":\"S-1:1\"")
            .contains("\"expiresAt\":\"2026-11-07T18:15:00Z\"").contains("\"quantity\":2");
        assertThat(inventory.available(product)).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("La cantidad se escribe igual aunque llegue como 2, 2.00 o 2.0000, y sin notación científica")
    void theQuantityIsWrittenTheSameWhateverItsScale() {
        UUID product = UUID.randomUUID();
        inventory.product(product, "cmp_1", "CAFE", "Café", "1000");

        gateway.reserve(CALLER, reservation(product, "2", "S-2:1", "llave-estable"));
        // Con otra escala, el cuerpo debe ser el mismo: si no, inventario respondería 409 por reusar la llave.
        gateway.reserve(CALLER, reservation(product, "2.0000", "S-2:1", "llave-estable"));
        gateway.reserve(CALLER, reservation(product, "2.00", "S-2:1", "llave-estable"));
        gateway.reserve(CALLER, reservation(product, "100", "S-3:1", "llave-cien"));

        var bodies = inventory.requestsTo("/stock/reservations").stream().map(FakeInventoryServer.Recorded::body).toList();
        assertThat(bodies.get(0)).isEqualTo(bodies.get(1)).isEqualTo(bodies.get(2));
        assertThat(bodies.get(3)).contains("\"quantity\":100").doesNotContain("E+");
        // Las repeticiones con la misma llave no descontaron otra vez.
        assertThat(inventory.available(product)).isEqualByComparingTo("898");
    }

    @Test
    @DisplayName("Si no alcanza el stock, inventario responde 422 con su mensaje y Ventas lo traduce a falta de stock")
    void insufficientStockIsTranslated() {
        UUID product = UUID.randomUUID();
        inventory.product(product, "cmp_1", "CAFE", "Café", "1");

        assertThatThrownBy(() -> gateway.reserve(CALLER, reservation(product, "5", "S-4:1", "llave-4")))
            .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    @DisplayName("Un 403, un 409 o un 422 que no es de stock se traducen a rechazado, con el mensaje de inventario")
    void otherRejectionsAreTranslated() {
        UUID product = UUID.randomUUID();
        inventory.product(product, "cmp_1", "CAFE", "Café", "10");

        for (int status : new int[] {403, 409, 422, 404}) {
            inventory.failPath("/stock/reservations", status);
            assertThatThrownBy(() -> gateway.reserve(CALLER, reservation(product, "1", "S-5:1", "llave-5-" + status)))
                .as("estado " + status)
                .isInstanceOf(InventoryRejectedException.class)
                .hasMessageContaining("Error interno del servidor");
        }
    }

    @Test
    @DisplayName("Un error del servidor, un inventario caído o uno muy lento se traducen a no disponible")
    void serverErrorsDownAndSlowInventoryAreUnavailable() {
        UUID product = UUID.randomUUID();
        inventory.product(product, "cmp_1", "CAFE", "Café", "10");

        inventory.failAllWith(503);
        assertThatThrownBy(() -> gateway.reserve(CALLER, reservation(product, "1", "S-6:1", "llave-6a")))
            .isInstanceOf(InventoryUnavailableException.class);
        inventory.failAllWith(0);

        var down = new HttpInventoryGateway("http://127.0.0.1:1", Duration.ofMillis(500), Duration.ofMillis(500));
        assertThatThrownBy(() -> down.reserve(CALLER, reservation(product, "1", "S-6:1", "llave-6b")))
            .isInstanceOf(InventoryUnavailableException.class);

        inventory.delay(800);
        var impatient = new HttpInventoryGateway(inventory.baseUrl(), Duration.ofSeconds(1), Duration.ofMillis(200));
        assertThatThrownBy(() -> impatient.reserve(CALLER, reservation(product, "1", "S-6:1", "llave-6c")))
            .isInstanceOf(InventoryUnavailableException.class);
    }

    @Test
    @DisplayName("Liberar, confirmar y devolver van a su ruta con la referencia y la llave, y repetir la llave no repite el efecto")
    void releaseCommitAndReturnGoToTheirRoutes() {
        UUID product = UUID.randomUUID();
        inventory.product(product, "cmp_1", "CAFE", "Café", "10");
        gateway.reserve(CALLER, reservation(product, "3", "S-7:1", "llave-7-reserva"));

        gateway.commit(CALLER, reference("S-7:1", null, "llave-7-commit"));
        gateway.commit(CALLER, reference("S-7:1", null, "llave-7-commit"));
        gateway.returnStock(CALLER, reference("S-7:1", "Venta cancelada", "llave-7-return"));

        assertThat(inventory.requestsTo("/stock/reservations/commit")).hasSize(2);
        var returned = inventory.requestsTo("/stock/returns").get(0);
        assertThat(returned.path()).isEqualTo("/api/v1/companies/cmp_1/branches/br_1/stock/returns");
        assertThat(returned.body()).contains("\"referenceType\":\"SALE\"").contains("\"referenceId\":\"S-7:1\"")
            .contains("\"reason\":\"Venta cancelada\"");
        assertThat(returned.headers()).containsEntry("idempotency-key", "llave-7-return");
        assertThat(inventory.available(product)).isEqualByComparingTo("10");

        gateway.reserve(CALLER, reservation(product, "2", "S-8:1", "llave-8-reserva"));
        gateway.release(CALLER, reference("S-8:1", "Pago inválido", "llave-8-release"));
        assertThat(inventory.requestsTo("/stock/reservations/release")).hasSize(1);
        assertThat(inventory.available(product)).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Confirmar una referencia que inventario no tiene apartada se traduce a rechazado")
    void committingAnUnknownReferenceIsRejected() {
        assertThatThrownBy(() -> gateway.commit(CALLER, reference("NO-EXISTE:1", null, "llave-9")))
            .isInstanceOf(InventoryRejectedException.class)
            .hasMessageContaining("no active reservation");
    }

    // ---- Ayudas ----

    private static final UUID WAREHOUSE = UUID.fromString("00000000-0000-0000-0000-00000000aaaa");

    private static StockReservation reservation(UUID product, String quantity, String referenceId, String key) {
        return new StockReservation("cmp_1", "br_1", WAREHOUSE, product, new BigDecimal(quantity), "PZA", "SALE",
            referenceId, Instant.parse("2026-11-07T18:15:00Z"), key);
    }

    private static StockReference reference(String referenceId, String reason, String key) {
        return new StockReference("cmp_1", "br_1", "SALE", referenceId, reason, key);
    }
}
