package com.jcode.jcomercy.sales.infra.web;

import com.jcode.jcomercy.sales.infra.SalesTokens;
import com.jcode.jcomercy.sales.infra.inventory.FakeInventoryServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobrar una venta de mostrador de punta a punta por la ruta REST: con el token de quien vende, contra un inventario falso
 * que se comporta como el real. Cubre el camino feliz, la repetición con la misma llave y cada falla que el cobro sabe
 * manejar.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SaleCheckoutIntegrationTest {

    private static final FakeInventoryServer INVENTORY;

    static {
        try {
            INVENTORY = FakeInventoryServer.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void inventoryUrl(DynamicPropertyRegistry registry) {
        registry.add("sales.inventory.base-url", INVENTORY::baseUrl);
        registry.add("sales.inventory.read-timeout-ms", () -> "2000");
    }

    @AfterAll
    static void stopInventory() {
        INVENTORY.close();
    }

    @Autowired private MockMvc mockMvc;

    private String company;
    private String branch;
    private String seller;
    private UUID cafe;
    private UUID galleta;
    private UUID warehouse;

    @BeforeEach
    void aCompanyWithTwoProducts() throws Exception {
        INVENTORY.failAllWith(0);
        INVENTORY.healPaths();
        company = "cmp_chk_" + UUID.randomUUID();
        branch = "br_1";
        seller = SalesTokens.bearer(company, List.of(branch), "sales.create sales.read");
        String manager = SalesTokens.bearer(company, List.of(branch), "sales.prices.write sales.read");
        cafe = UUID.randomUUID();
        galleta = UUID.randomUUID();
        warehouse = UUID.randomUUID();
        INVENTORY.product(cafe, company, "CAFE", "Café", "10");
        INVENTORY.product(galleta, company, "GALLETA", "Galleta", "5");
        setPrice(manager, cafe, "45.50");
        setPrice(manager, galleta, "10.00");
    }

    @Test
    @DisplayName("Una venta se cobra: 201 con folio, precios de la lista, cambio, y el stock baja en inventario")
    void aSaleIsChargedAndTheStockGoesDown() throws Exception {
        String key = UUID.randomUUID().toString();

        checkout(seller, key, body(List.of(line(cafe, "2"), line(galleta, "1")), "{\"method\":\"CASH\",\"amount\":200}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.folio").value("TCK-000001"))
            .andExpect(jsonPath("$.channel").value("MOSTRADOR"))
            .andExpect(jsonPath("$.total").value(101.0))
            .andExpect(jsonPath("$.received").value(200.0))
            .andExpect(jsonPath("$.change").value(99.0))
            .andExpect(jsonPath("$.currency").value("MXN"))
            .andExpect(jsonPath("$.lines", hasSize(2)))
            .andExpect(jsonPath("$.lines[0].sku").value("CAFE"))
            .andExpect(jsonPath("$.lines[0].unitPrice").value(45.5))
            .andExpect(jsonPath("$.lines[0].lineTotal").value(91.0))
            .andExpect(jsonPath("$.payments[0].method").value("CASH"));

        assertThat(INVENTORY.available(cafe)).isEqualByComparingTo("8");
        assertThat(INVENTORY.available(galleta)).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("Cada petición a inventario lleva el token de quien vende y una llave que sale de la venta")
    void everyInventoryRequestCarriesTheSellerTokenAndASaleKey() throws Exception {
        String key = UUID.randomUUID().toString();
        INVENTORY.requests.clear();

        checkout(seller, key, body(List.of(line(cafe, "1")), "{\"method\":\"CASH\",\"amount\":50}")).andExpect(status().isCreated());

        assertThat(INVENTORY.requests).isNotEmpty().allSatisfy(request ->
            assertThat(request.headers()).containsEntry("authorization", seller));
        assertThat(INVENTORY.requestsTo("/stock/").stream().map(r -> r.headers().get("idempotency-key")))
            .allSatisfy(k -> assertThat(k).startsWith("sale-"));
    }

    @Test
    @DisplayName("Repetir la misma llave devuelve la misma venta con 200, sin tocar inventario ni gastar otro folio")
    void repeatingTheKeyReturnsTheSameSale() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = body(List.of(line(cafe, "1")), "{\"method\":\"CASH\",\"amount\":50}");
        String first = checkout(seller, key, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int requestsBefore = INVENTORY.requests.size();

        checkout(seller, key, body).andExpect(status().isOk())
            .andExpect(jsonPath("$.saleId").value(com.jayway.jsonpath.JsonPath.<String>read(first, "$.saleId")))
            .andExpect(jsonPath("$.folio").value("TCK-000001"));

        assertThat(INVENTORY.requests).hasSize(requestsBefore);
        assertThat(INVENTORY.available(cafe)).isEqualByComparingTo("9");
        checkout(seller, UUID.randomUUID().toString(), body).andExpect(jsonPath("$.folio").value("TCK-000002"));
    }

    @Test
    @DisplayName("Una cantidad con decimales se cobra con su importe redondeado y llega a inventario tal cual")
    void aDecimalQuantityIsChargedAndSentAsIs() throws Exception {
        UUID queso = UUID.randomUUID();
        INVENTORY.product(queso, company, "QUESO", "Queso", "10");
        setPrice(SalesTokens.bearer(company, List.of(branch), "sales.prices.write"), queso, "33.33");

        checkout(seller, UUID.randomUUID().toString(), body(List.of(line(queso, "0.5")), "{\"method\":\"CASH\",\"amount\":20}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.total").value(16.67))
            .andExpect(jsonPath("$.lines[0].quantity").value(0.5));

        assertThat(INVENTORY.available(queso)).isEqualByComparingTo("9.5");
    }

    @Test
    @DisplayName("Un producto sin precio responde 400 con qué hacer y no toca el stock")
    void aProductWithoutAPriceIsRejected() throws Exception {
        UUID agua = UUID.randomUUID();
        INVENTORY.product(agua, company, "AGUA", "Agua", "5");

        checkout(seller, UUID.randomUUID().toString(), body(List.of(line(agua, "1")), "{\"method\":\"CASH\",\"amount\":20}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SALES_RULE_VIOLATION"))
            .andExpect(jsonPath("$.message").value("Fija el precio de AGUA antes de venderlo."));

        assertThat(INVENTORY.available(agua)).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("Un pago que no cubre el total responde 400 y no aparta nada")
    void aPaymentThatDoesNotCoverTheTotalIsRejected() throws Exception {
        INVENTORY.requests.clear();

        checkout(seller, UUID.randomUUID().toString(), body(List.of(line(cafe, "2")), "{\"method\":\"CASH\",\"amount\":50}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Faltan 41.00 para cubrir el total de 91.00."));

        assertThat(INVENTORY.requestsTo("/stock/")).isEmpty();
        assertThat(INVENTORY.available(cafe)).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Si no alcanza el stock responde 409, la venta queda cancelada y lo apartado se libera")
    void insufficientStockRespondsConflictAndReleasesWhatWasReserved() throws Exception {
        checkout(seller, UUID.randomUUID().toString(),
            body(List.of(line(cafe, "2"), line(galleta, "9")), "{\"method\":\"CASH\",\"amount\":500}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("No alcanza el stock de GALLETA. Revisa las existencias o baja la cantidad."));

        assertThat(INVENTORY.available(cafe)).isEqualByComparingTo("10");
        assertThat(INVENTORY.available(galleta)).isEqualByComparingTo("5");
        mockMvc.perform(get("/api/v1/companies/{c}/branches/{b}/sales", company, branch).header("Authorization", seller))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].status").value("CANCELLED"))
            .andExpect(jsonPath("$[0].cancelReason").value(startsWith("Sin existencias suficientes de GALLETA")));
    }

    @Test
    @DisplayName("Si inventario no responde, responde 503 y no queda ninguna venta guardada")
    void anUnavailableInventoryRespondsServiceUnavailable() throws Exception {
        INVENTORY.failAllWith(503);

        checkout(seller, UUID.randomUUID().toString(), body(List.of(line(cafe, "1")), "{\"method\":\"CASH\",\"amount\":50}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("INVENTORY_UNAVAILABLE"));

        mockMvc.perform(get("/api/v1/companies/{c}/branches/{b}/sales", company, branch).header("Authorization", seller))
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Si falla la confirmación, responde 503 con la venta confirmándose, y repetir la petición la termina")
    void aFailedConfirmationIsFinishedByRepeatingTheRequest() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = body(List.of(line(cafe, "2"), line(galleta, "1")), "{\"method\":\"CASH\",\"amount\":101}");
        INVENTORY.failPath("/reservations/commit", 503);

        String pending = checkout(seller, key, body)
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("SALE_CONFIRMATION_PENDING"))
            .andExpect(jsonPath("$.saleId").exists())
            .andReturn().getResponse().getContentAsString();
        String saleId = com.jayway.jsonpath.JsonPath.read(pending, "$.saleId");
        mockMvc.perform(get("/api/v1/companies/{c}/branches/{b}/sales/{s}", company, branch, saleId).header("Authorization", seller))
            .andExpect(jsonPath("$.status").value("CONFIRMING"))
            .andExpect(jsonPath("$.folio").doesNotExist());

        INVENTORY.healPaths();
        checkout(seller, key, body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.saleId").value(saleId))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.folio").value("TCK-000001"));

        // Lo apartado en el primer intento no se apartó otra vez, y la llave estable evitó un 409 de inventario.
        assertThat(INVENTORY.available(cafe)).isEqualByComparingTo("8");
        assertThat(INVENTORY.available(galleta)).isEqualByComparingTo("4");
        // Solo las peticiones de esta empresa: el inventario falso lo comparten todas las pruebas de la clase.
        assertThat(INVENTORY.requestsTo("/companies/" + company + "/").stream()
            .filter(r -> r.path().endsWith("/stock/reservations")).count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Sin sales.create, sin la sucursal en el token o sin token no se cobra")
    void withoutPermissionScopeOrTokenNothingIsCharged() throws Exception {
        String body = body(List.of(line(cafe, "1")), "{\"method\":\"CASH\",\"amount\":50}");

        checkout(SalesTokens.bearer(company, List.of(branch), "sales.read"), UUID.randomUUID().toString(), body)
            .andExpect(status().isForbidden());
        checkout(SalesTokens.bearer(company, List.of("br_otra"), "sales.create"), UUID.randomUUID().toString(), body)
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/sales", company, branch)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());

        assertThat(INVENTORY.available(cafe)).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Una petición mal formada responde 400: sin llave, sin productos, sin pagos o con cantidad cero")
    void malformedRequestsAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/sales", company, branch).header("Authorization", seller)
                .contentType(MediaType.APPLICATION_JSON).content(body(List.of(line(cafe, "1")), "{\"method\":\"CASH\",\"amount\":50}")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        checkout(seller, UUID.randomUUID().toString(), body(List.of(), "{\"method\":\"CASH\",\"amount\":50}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        checkout(seller, UUID.randomUUID().toString(), "{\"warehouseId\":\"" + warehouse + "\",\"lines\":[" + line(cafe, "1") + "],\"payments\":[]}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        checkout(seller, UUID.randomUUID().toString(), body(List.of(line(cafe, "0")), "{\"method\":\"CASH\",\"amount\":50}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        checkout(seller, UUID.randomUUID().toString(), body(List.of(line(cafe, "1")), "{\"method\":\"BITCOIN\",\"amount\":50}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    // ---- Ayudas ----

    private ResultActions checkout(String token, String key, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/sales", company, branch).header("Authorization", token)
            .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String body(List<String> lines, String payment) {
        return "{\"warehouseId\":\"" + warehouse + "\",\"lines\":[" + String.join(",", lines) + "],\"payments\":[" + payment + "]}";
    }

    private static String line(UUID product, String quantity) {
        return "{\"productId\":\"" + product + "\",\"quantity\":" + quantity + "}";
    }

    private void setPrice(String manager, UUID product, String amount) throws Exception {
        mockMvc.perform(put("/api/v1/companies/{c}/products/{p}/price", company, product).header("Authorization", manager)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + ",\"currency\":\"MXN\"}"))
            .andExpect(status().isOk());
    }
}
