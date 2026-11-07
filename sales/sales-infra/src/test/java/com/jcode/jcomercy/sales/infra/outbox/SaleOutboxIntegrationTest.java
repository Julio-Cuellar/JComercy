package com.jcode.jcomercy.sales.infra.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jcode.jcomercy.sales.application.port.SaleEventsPort;
import com.jcode.jcomercy.sales.infra.SalesTokens;
import com.jcode.jcomercy.sales.infra.inventory.FakeInventoryServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los avisos de ventas en el Outbox: una venta completada y una completada que se cancela dejan su evento, en la misma
 * transacción que cambia la venta; una que se cancela antes de completarse no deja nada.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SaleOutboxIntegrationTest {

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
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @SpyBean private SaleEventsPort events;

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
        Mockito.reset(events);
        company = "cmp_evt_" + UUID.randomUUID();
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
    @DisplayName("Una venta cobrada deja un evento de venta completada, pendiente de publicar, con el contenido que el contrato declara")
    void aChargedSaleLeavesACompletedEvent() throws Exception {
        String saleId = sell(UUID.randomUUID().toString());

        List<Map<String, Object>> rows = outbox();
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("EVENT_TYPE")).isEqualTo("sales.sale.completed");
        assertThat(row.get("STATUS")).isEqualTo("PENDING");
        assertThat(row.get("ACTOR_USER_ID")).isEqualTo("usr_cajera");
        assertThat(row.get("BRANCH_ID")).isEqualTo(branch);
        assertThat(row.get("SCHEMA_VERSION")).isEqualTo("1.0");
        assertThat(((Number) row.get("SEQUENCE")).longValue()).isPositive();
        JsonNode data = mapper.readTree((String) row.get("PAYLOAD"));
        assertThat(data.path("saleId").asText()).isEqualTo(saleId);
        assertThat(data.path("folio").asText()).isEqualTo("TCK-000001");
        assertThat(data.path("channel").asText()).isEqualTo("MOSTRADOR");
        assertThat(data.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(data.path("warehouseId").asText()).isEqualTo(warehouse.toString());
        assertThat(data.path("currency").asText()).isEqualTo("MXN");
        assertThat(data.path("total").decimalValue()).isEqualByComparingTo("101.00");
        assertThat(data.path("lines")).hasSize(2);
        assertThat(data.path("lines").get(0).path("productId").asText()).isEqualTo(cafe.toString());
        assertThat(data.path("lines").get(0).path("quantity").decimalValue()).isEqualByComparingTo("2");
        assertThat(data.path("lines").get(0).path("unitPrice").decimalValue()).isEqualByComparingTo("45.50");
        assertThat(data.path("lines").get(0).path("lineTotal").decimalValue()).isEqualByComparingTo("91.00");
        assertThat(data.has("reason")).isFalse();
    }

    @Test
    @DisplayName("Cancelar una venta completada deja un evento de venta cancelada con el motivo y quien la canceló")
    void cancellingACompletedSaleLeavesACancelledEvent() throws Exception {
        String saleId = sell(UUID.randomUUID().toString());

        cancel(saleId, "El cliente devolvió todo").andExpect(status().isOk());

        List<Map<String, Object>> rows = outbox();
        assertThat(rows).hasSize(2);
        Map<String, Object> row = rows.get(1);
        assertThat(row.get("EVENT_TYPE")).isEqualTo("sales.sale.cancelled");
        assertThat(row.get("ACTOR_USER_ID")).isEqualTo("usr_gerente");
        JsonNode data = mapper.readTree((String) row.get("PAYLOAD"));
        assertThat(data.path("saleId").asText()).isEqualTo(saleId);
        assertThat(data.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(data.path("reason").asText()).isEqualTo("El cliente devolvió todo");
        assertThat(data.path("folio").asText()).isEqualTo("TCK-000001");
        // La cantidad no trae los ceros de la base de datos (2.0000).
        assertThat(mapper.writeValueAsString(data.path("lines").get(0).path("quantity"))).isEqualTo("2");
    }

    @Test
    @DisplayName("Una venta que se cancela antes de completarse no deja ningún evento")
    void aSaleCancelledBeforeCompletingLeavesNoEvent() throws Exception {
        checkout(UUID.randomUUID().toString(), line(galleta, "9"), "500").andExpect(status().isConflict());

        assertThat(outbox()).isEmpty();
    }

    @Test
    @DisplayName("El número de secuencia crece con el orden en que se registran los eventos")
    void theSequenceGrowsWithTheOrderOfRegistration() throws Exception {
        sell(UUID.randomUUID().toString());
        sell(UUID.randomUUID().toString());

        List<Map<String, Object>> rows = outbox();
        assertThat(rows).hasSize(2);
        assertThat(((Number) rows.get(1).get("SEQUENCE")).longValue()).isGreaterThan(((Number) rows.get(0).get("SEQUENCE")).longValue());
    }

    @Test
    @DisplayName("Si falla guardar el evento, la venta no se completa y el folio no se gasta; al repetir sale todo junto")
    void theEventTheSaleAndTheFolioAreSavedTogether() throws Exception {
        String key = UUID.randomUUID().toString();
        Mockito.doThrow(new IllegalStateException("el Outbox no responde")).when(events).completed(any(), any());

        checkout(key, line(cafe, "2"), "91").andExpect(status().isInternalServerError());

        List<Map<String, Object>> sales = salesOfTheBranch();
        assertThat(sales).hasSize(1);
        assertThat(sales.get(0).get("status")).isEqualTo("CONFIRMING");
        assertThat(sales.get(0).get("folio")).isNull();
        assertThat(outbox()).isEmpty();

        Mockito.reset(events);
        checkout(key, line(cafe, "2"), "91").andExpect(status().isCreated());

        assertThat(outbox()).hasSize(1);
        assertThat(salesOfTheBranch().get(0).get("folio")).isEqualTo("TCK-000001");
    }

    @Test
    @DisplayName("Si falla guardar el evento de cancelación, la venta sigue completada; al repetir se cancela y deja su evento")
    void theCancellationAndItsEventAreSavedTogether() throws Exception {
        String saleId = sell(UUID.randomUUID().toString());
        Mockito.doThrow(new IllegalStateException("el Outbox no responde")).when(events).cancelled(any(), any(), any());

        cancel(saleId, "El cliente devolvió todo").andExpect(status().isInternalServerError());

        assertThat(salesOfTheBranch().get(0).get("status")).isEqualTo("COMPLETED");
        assertThat(outbox()).hasSize(1);

        Mockito.reset(events);
        cancel(saleId, "El cliente devolvió todo").andExpect(status().isOk());

        assertThat(salesOfTheBranch().get(0).get("status")).isEqualTo("CANCELLED");
        assertThat(outbox()).hasSize(2);
        assertThat(outbox().get(1).get("EVENT_TYPE")).isEqualTo("sales.sale.cancelled");
    }

    // ---- Ayudas ----

    private List<Map<String, Object>> outbox() {
        return jdbc.queryForList("SELECT * FROM sales.outbox_event WHERE company_id = ? ORDER BY sequence", company);
    }

    private List<Map<String, Object>> salesOfTheBranch() throws Exception {
        String body = mockMvc.perform(get("/api/v1/companies/{c}/branches/{b}/sales", company, branch).header("Authorization", seller))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$");
    }

    /** Una venta cobrada: dos cafés y una galleta, 101.00. */
    private String sell(String key) throws Exception {
        String body = checkout(key, line(cafe, "2") + "," + line(galleta, "1"), "200")
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.saleId");
    }

    private ResultActions checkout(String key, String lines, String paid) throws Exception {
        return mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/sales", company, branch).header("Authorization", seller)
            .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
            .content("{\"warehouseId\":\"" + warehouse + "\",\"lines\":[" + lines + "],"
                + "\"payments\":[{\"method\":\"CASH\",\"amount\":" + paid + "}]}"));
    }

    private static String line(UUID product, String quantity) {
        return "{\"productId\":\"" + product + "\",\"quantity\":" + quantity + "}";
    }

    private ResultActions cancel(String saleId, String reason) throws Exception {
        return mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/sales/{s}/cancellation", company, branch, saleId)
            .header("Authorization", manager).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}"));
    }

    private void setPrice(UUID product, String amount) throws Exception {
        mockMvc.perform(put("/api/v1/companies/{c}/products/{p}/price", company, product).header("Authorization", manager)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + ",\"currency\":\"MXN\"}"))
            .andExpect(status().isOk());
    }
}
