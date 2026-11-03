package com.jcode.jcomercy.inventory.infra;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class StockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String token;
    private final String companyId = "cmp_stk_test_100";
    private final String branchId = "br_stk_test_100";

    @BeforeEach
    void setUp() {
        SecretKey key = Keys.hmacShaKeyFor("local-only-change-me-012345678901234567890123456789".getBytes(StandardCharsets.UTF_8));
        token = "Bearer " + Jwts.builder()
                .subject("user-test-stk")
                .issuer("jcomercy")
                .claim("companyIds", List.of(companyId))
                .claim("branchIds", List.of(branchId))
                .claim("permissions", "INVENTORY_WRITE INVENTORY_READ")
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("Debe registrar entradas, salidas y ajustes de inventario, actualizando saldos y kárdex")
    void shouldExecuteFullStockOperationsFlow() throws Exception {
        // 1. Crear almacén de sucursal
        String createWhJson = """
            {
                "code": "ALM-STK-01",
                "name": "Almacén Principal Pruebas",
                "type": "GENERAL",
                "defaultWarehouse": true,
                "timezone": "America/Mexico_City",
                "currency": "MXN"
            }
            """;

        String whResponse = mockMvc.perform(post("/api/v1/companies/{companyId}/branches/{branchId}/warehouses", companyId, branchId)
                .header("Authorization", token)
                .header("Idempotency-Key", "idem_wh_stk_01")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createWhJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String warehouseIdStr = com.jayway.jsonpath.JsonPath.read(whResponse, "$.id");

        // Activar almacén
        mockMvc.perform(post("/api/v1/warehouses/{warehouseId}/activate", warehouseIdStr)
                .header("Authorization", token)
                .header("Idempotency-Key", "idem_wh_act_01"))
                .andExpect(status().isOk());

        // 2. Crear ubicación en almacén
        String createLocJson = """
            {
                "code": "PASILLO-A1",
                "name": "Pasillo A Estante 1",
                "type": "INTERNAL"
            }
            """;

        String locResponse = mockMvc.perform(post("/api/v1/warehouses/{warehouseId}/locations", warehouseIdStr)
                .header("Authorization", token)
                .header("Idempotency-Key", "idem_loc_stk_01")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createLocJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String locationIdStr = com.jayway.jsonpath.JsonPath.read(locResponse, "$.id");

        // 3. Crear producto
        String createProdJson = """
            {
                "sku": "PROD-STK-FLOW-01",
                "name": "Producto prueba flujo stock",
                "stockUnitCode": "PZA",
                "trackingMode": "NONE",
                "allowNegativeStock": false
            }
            """;

        String prodResponse = mockMvc.perform(post("/api/v1/companies/{companyId}/products", companyId)
                .header("Authorization", token)
                .header("Idempotency-Key", "idem_prod_stk_01")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createProdJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String productIdStr = com.jayway.jsonpath.JsonPath.read(prodResponse, "$.id");

        // 4. Registrar Entrada de Stock (RECEIPT +50.0)
        String receiveJson = String.format("""
            {
                "warehouseId": "%s",
                "locationId": "%s",
                "productId": "%s",
                "quantity": 50.0,
                "unitCode": "PZA",
                "unitCost": 15.50,
                "inventoryStatus": "AVAILABLE",
                "referenceType": "PURCHASE_ORDER",
                "referenceId": "PO-100200"
            }
            """, warehouseIdStr, locationIdStr, productIdStr);

        mockMvc.perform(post("/api/v1/companies/{companyId}/branches/{branchId}/stock/receipts", companyId, branchId)
                .header("Authorization", token)
                .header("Idempotency-Key", "idem_rec_01")
                .contentType(MediaType.APPLICATION_JSON)
                .content(receiveJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movement.type", is("RECEIPT")))
                .andExpect(jsonPath("$.movement.quantity", is(50.0)))
                .andExpect(jsonPath("$.balance.onHand", is(50.0)));

        // 5. Registrar Salida de Stock (ISSUE -20.0) -> Queda 30.0
        String issueJson = String.format("""
            {
                "warehouseId": "%s",
                "locationId": "%s",
                "productId": "%s",
                "quantity": 20.0,
                "unitCode": "PZA",
                "inventoryStatus": "AVAILABLE",
                "referenceType": "SALE_ORDER",
                "referenceId": "SO-500100"
            }
            """, warehouseIdStr, locationIdStr, productIdStr);

        mockMvc.perform(post("/api/v1/companies/{companyId}/branches/{branchId}/stock/issues", companyId, branchId)
                .header("Authorization", token)
                .header("Idempotency-Key", "idem_iss_01")
                .contentType(MediaType.APPLICATION_JSON)
                .content(issueJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movement.type", is("ISSUE")))
                .andExpect(jsonPath("$.movement.quantity", is(20.0)))
                .andExpect(jsonPath("$.balance.onHand", is(30.0)));

        // 6. Intentar Salida con Stock Insuficiente (Intentar -100.0 cuando solo hay 30.0) -> Expect 422 Unprocessable Entity
        String issueTooMuchJson = String.format("""
            {
                "warehouseId": "%s",
                "locationId": "%s",
                "productId": "%s",
                "quantity": 100.0,
                "unitCode": "PZA",
                "inventoryStatus": "AVAILABLE"
            }
            """, warehouseIdStr, locationIdStr, productIdStr);

        mockMvc.perform(post("/api/v1/companies/{companyId}/branches/{branchId}/stock/issues", companyId, branchId)
                .header("Authorization", token)
                .header("Idempotency-Key", "idem_iss_too_much")
                .contentType(MediaType.APPLICATION_JSON)
                .content(issueTooMuchJson))
                .andExpect(status().isUnprocessableEntity());

        // 7. Registrar Ajuste Negativo (ADJUSTMENT_OUT -5.0) -> Queda 25.0
        String adjustOutJson = String.format("""
            {
                "warehouseId": "%s",
                "locationId": "%s",
                "productId": "%s",
                "quantity": 5.0,
                "unitCode": "PZA",
                "reason": "Merma por empaque dañado",
                "direction": "OUT",
                "inventoryStatus": "AVAILABLE"
            }
            """, warehouseIdStr, locationIdStr, productIdStr);

        mockMvc.perform(post("/api/v1/companies/{companyId}/branches/{branchId}/stock/adjustments", companyId, branchId)
                .header("Authorization", token)
                .header("Idempotency-Key", "idem_adj_out")
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustOutJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movement.type", is("ADJUSTMENT_OUT")))
                .andExpect(jsonPath("$.balance.onHand", is(25.0)));

        // 8. Registrar Ajuste Positivo (ADJUSTMENT_IN +15.0) -> Queda 40.0
        String adjustInJson = String.format("""
            {
                "warehouseId": "%s",
                "locationId": "%s",
                "productId": "%s",
                "quantity": 15.0,
                "unitCode": "PZA",
                "reason": "Encontrado en inventario anual",
                "direction": "IN",
                "inventoryStatus": "AVAILABLE"
            }
            """, warehouseIdStr, locationIdStr, productIdStr);

        mockMvc.perform(post("/api/v1/companies/{companyId}/branches/{branchId}/stock/adjustments", companyId, branchId)
                .header("Authorization", token)
                .header("Idempotency-Key", "idem_adj_in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustInJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movement.type", is("ADJUSTMENT_IN")))
                .andExpect(jsonPath("$.balance.onHand", is(40.0)));

        // 9. Consultar Saldos
        mockMvc.perform(get("/api/v1/companies/{companyId}/stock/balances", companyId)
                .param("productId", productIdStr)
                .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].onHand", is(40.0)));

        // 10. Consultar Kárdex / Movimientos
        mockMvc.perform(get("/api/v1/companies/{companyId}/stock/movements", companyId)
                .param("productId", productIdStr)
                .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)));
    }
}
