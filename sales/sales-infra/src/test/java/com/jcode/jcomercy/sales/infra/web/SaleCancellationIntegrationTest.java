package com.jcode.jcomercy.sales.infra.web;

import com.jayway.jsonpath.JsonPath;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cancelar una venta por la ruta REST, de punta a punta contra un inventario falso que se comporta como el real: quién
 * puede, qué pasa con el stock y qué responde cuando inventario falla.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SaleCancellationIntegrationTest {

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
    private String manager;
    private UUID cafe;
    private UUID galleta;
    private UUID warehouse;

    @BeforeEach
    void aCompanyWithTwoProducts() throws Exception {
        INVENTORY.failAllWith(0);
        INVENTORY.healPaths();
        company = "cmp_cnc_" + UUID.randomUUID();
        branch = "br_1";
        seller = SalesTokens.bearer("usr_cajera", company, List.of(branch), "sales.create sales.read");
        manager = SalesTokens.bearer("usr_gerente", company, List.of(branch), "sales.cancel sales.read sales.prices.write");
        cafe = UUID.randomUUID();
        galleta = UUID.randomUUID();
        warehouse = UUID.randomUUID();
        INVENTORY.product(cafe, company, "CAFE", "Café", "10");
        INVENTORY.product(galleta, company, "GALLETA", "Galleta", "5");
        setPrice(cafe, "45.50");
        setPrice(galleta, "10.00");
    }

    @Test
    @DisplayName("Cancelar una venta completada responde 200, la deja cancelada con su folio y su motivo, y el stock vuelve")
    void cancellingACompletedSaleReturnsTheStock() throws Exception {
        String saleId = sell();
        assertThat(INVENTORY.available(cafe)).isEqualByComparingTo("8");

        cancel(manager, saleId, "El cliente devolvió todo")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelReason").value("El cliente devolvió todo"))
            .andExpect(jsonPath("$.cancelledAt").exists())
            .andExpect(jsonPath("$.folio").value("TCK-000001"));

        assertThat(INVENTORY.available(cafe)).isEqualByComparingTo("10");
        assertThat(INVENTORY.available(galleta)).isEqualByComparingTo("5");
        mockMvc.perform(get("/api/v1/companies/{c}/branches/{b}/sales/{s}", company, branch, saleId).header("Authorization", seller))
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("La devolución a inventario lleva el token de quien cancela y una llave que sale de la venta")
    void theReturnCarriesTheCancellerTokenAndASaleKey() throws Exception {
        String saleId = sell();
        INVENTORY.requests.clear();

        cancel(manager, saleId, "Se pasó el precio").andExpect(status().isOk());

        var returns = INVENTORY.requestsTo("/stock/returns");
        assertThat(returns).hasSize(2);
        assertThat(returns).allSatisfy(request -> assertThat(request.headers()).containsEntry("authorization", manager));
        assertThat(returns.get(0).headers().get("idempotency-key")).isEqualTo("sale-" + saleId + "-return-1");
        assertThat(returns.get(0).body()).contains("\"reason\":\"Se pasó el precio\"");
    }

    @Test
    @DisplayName("Una venta ya cancelada no se cancela otra vez: 409")
    void aCancelledSaleCannotBeCancelledAgain() throws Exception {
        String saleId = sell();
        cancel(manager, saleId, "Primera vez").andExpect(status().isOk());

        cancel(manager, saleId, "Otra vez")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("La venta ya está cancelada."));

        assertThat(INVENTORY.available(cafe)).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Sin sales.cancel, sin la sucursal en el token o sin token no se cancela")
    void withoutPermissionScopeOrTokenNothingIsCancelled() throws Exception {
        String saleId = sell();

        cancel(seller, saleId, "x").andExpect(status().isForbidden());
        cancel(SalesTokens.bearer(company, List.of("br_otra"), "sales.cancel"), saleId, "x").andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/sales/{s}/cancellation", company, branch, saleId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
            .andExpect(status().isUnauthorized());

        assertThat(INVENTORY.available(cafe)).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("Una venta que no existe responde 404 con un mensaje claro")
    void anUnknownSaleIsNotFound() throws Exception {
        cancel(manager, UUID.randomUUID().toString(), "x")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("No encontramos esa venta en esta sucursal."));
    }

    @Test
    @DisplayName("Sin motivo, o con un cuerpo que no es JSON, responde 400 y no toca el stock")
    void aMissingReasonIsRejected() throws Exception {
        String saleId = sell();

        cancel(manager, saleId, "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        cancel(manager, saleId, "x".repeat(501)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/sales/{s}/cancellation", company, branch, saleId)
                .header("Authorization", manager).contentType(MediaType.APPLICATION_JSON).content("no es json"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        assertThat(INVENTORY.available(cafe)).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("Si inventario no responde, responde 503, la venta sigue completada y repetir la cancelación la termina")
    void anUnavailableInventoryLeavesTheSaleCompletedAndTheCancellationCanBeRepeated() throws Exception {
        String saleId = sell();
        INVENTORY.failPath("/stock/returns", 503);

        cancel(manager, saleId, "El cliente devolvió todo")
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("INVENTORY_UNAVAILABLE"));
        mockMvc.perform(get("/api/v1/companies/{c}/branches/{b}/sales/{s}", company, branch, saleId).header("Authorization", seller))
            .andExpect(jsonPath("$.status").value("COMPLETED"));

        INVENTORY.healPaths();
        cancel(manager, saleId, "El cliente devolvió todo").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        // Se devolvió una sola vez, aunque la cancelación se pidió dos veces.
        assertThat(INVENTORY.available(cafe)).isEqualByComparingTo("10");
        assertThat(INVENTORY.available(galleta)).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("Si inventario rechaza devolver, responde 409 con la razón y la venta sigue completada")
    void aRejectedReturnKeepsTheSaleCompleted() throws Exception {
        String saleId = sell();
        INVENTORY.failPath("/stock/returns", 403);

        cancel(manager, saleId, "x")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Inventario no pudo devolver el stock de CAFE")));

        mockMvc.perform(get("/api/v1/companies/{c}/branches/{b}/sales/{s}", company, branch, saleId).header("Authorization", seller))
            .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("Una venta que se está confirmando no se cancela: 409")
    void aConfirmingSaleCannotBeCancelled() throws Exception {
        INVENTORY.failPath("/reservations/commit", 503);
        String pending = sellRaw(UUID.randomUUID().toString()).andExpect(status().isServiceUnavailable())
            .andReturn().getResponse().getContentAsString();
        String saleId = JsonPath.read(pending, "$.saleId");

        cancel(manager, saleId, "Error")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Esta venta se está confirmando. Espera a que termine para cancelarla."));
    }

    // ---- Ayudas ----

    /** Una venta cobrada: dos cafés y una galleta, 101.00. Devuelve su identificador. */
    private String sell() throws Exception {
        String body = sellRaw(UUID.randomUUID().toString()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.saleId");
    }

    private ResultActions sellRaw(String key) throws Exception {
        return mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/sales", company, branch).header("Authorization", seller)
            .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
            .content("{\"warehouseId\":\"" + warehouse + "\",\"lines\":[{\"productId\":\"" + cafe + "\",\"quantity\":2},"
                + "{\"productId\":\"" + galleta + "\",\"quantity\":1}],\"payments\":[{\"method\":\"CASH\",\"amount\":101}]}"));
    }

    private ResultActions cancel(String token, String saleId, String reason) throws Exception {
        return mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/sales/{s}/cancellation", company, branch, saleId)
            .header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}"));
    }

    private void setPrice(UUID product, String amount) throws Exception {
        mockMvc.perform(put("/api/v1/companies/{c}/products/{p}/price", company, product).header("Authorization", manager)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + ",\"currency\":\"MXN\"}"))
            .andExpect(status().isOk());
    }
}
