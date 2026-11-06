package com.jcode.jcomercy.inventory.infra;

import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La restricción {@code uk_stock_identity} de {@code inventory.stock_balance}. Un saldo se identifica por almacén,
 * ubicación, producto, lote, serie y estado, pero en un producto sin lote ni serie esas dos columnas son nulas, y para
 * una restricción única normal dos nulos son distintos: la base de datos dejaba entrar dos saldos de la misma
 * mercancía. Solo el bloqueo de la ubicación en la aplicación lo evitaba (ERR-017). Aquí se prueba la base de datos
 * sola, insertando por SQL, sin pasar por la aplicación.
 */
@SpringBootTest(properties = "server.port=0")
@AutoConfigureMockMvc
class StockIdentityUniquenessIntegrationTest {

    private static final String SECRET = "local-only-change-me-012345678901234567890123456789";
    private static final String DUPLICATE_OF = """
        INSERT INTO inventory.stock_balance (id, company_id, branch_id, warehouse_id, location_id, product_id, lot_id,
            serial_number, expiration_date, inventory_status, on_hand, reserved, unit_cost, costing_method, version)
        SELECT ?, company_id, branch_id, warehouse_id, location_id, product_id, ?, serial_number, expiration_date,
            inventory_status, on_hand, reserved, unit_cost, costing_method, version
        FROM inventory.stock_balance WHERE id = ?
        """;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("La base de datos rechaza un segundo saldo de la misma mercancía sin lote ni serie")
    void theDatabaseRejectsASecondBalanceForTheSameGoodsWithoutLotOrSerial() throws Exception {
        UUID balance = receiveOneBalance();

        assertThatThrownBy(() -> jdbc.update(DUPLICATE_OF, UUID.randomUUID(), null, balance))
            .as("segundo saldo con la misma identidad y lote y serie nulos")
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count(balance)).as("saldos de ese producto").isEqualTo(1);
    }

    @Test
    @DisplayName("Un saldo con otro lote sí es otra mercancía y la base de datos lo admite")
    void aBalanceWithAnotherLotIsOtherGoodsAndIsAccepted() throws Exception {
        UUID balance = receiveOneBalance();

        jdbc.update(DUPLICATE_OF, UUID.randomUUID(), "LOTE-B", balance);

        assertThat(count(balance)).as("saldos de ese producto, uno por lote").isEqualTo(2);
    }

    private int count(UUID balance) {
        return jdbc.queryForObject("""
            SELECT COUNT(*) FROM inventory.stock_balance WHERE product_id = (SELECT product_id FROM inventory.stock_balance WHERE id = ?)
            """, Integer.class, balance);
    }

    /** Recibe mercancía de un producto sin seguimiento y devuelve el saldo que quedó. */
    private UUID receiveOneBalance() throws Exception {
        String company = "cmp_uk_" + UUID.randomUUID().toString().substring(0, 8);
        String branch = "br_uk_1";
        String token = token(company, branch);
        String warehouse = JsonPath.read(create(token, "/api/v1/companies/" + company + "/branches/" + branch + "/warehouses",
            """
            {"code":"ALM-UK-01","name":"Almacén de unicidad","type":"GENERAL","defaultWarehouse":false,
             "timezone":"America/Mexico_City","currency":"MXN"}
            """), "$.id");
        mockMvc.perform(post("/api/v1/warehouses/{id}/activate", warehouse)
                .header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString()))
            .andExpect(status().isOk());
        String location = JsonPath.read(create(token, "/api/v1/warehouses/" + warehouse + "/locations",
            "{\"code\":\"PASILLO-A\",\"name\":\"Pasillo A\",\"type\":\"INTERNAL\"}"), "$.id");
        String product = JsonPath.read(create(token, "/api/v1/companies/" + company + "/products",
            "{\"sku\":\"UK-SKU-1\",\"name\":\"Producto de unicidad\",\"stockUnitCode\":\"PZA\",\"trackingMode\":\"NONE\",\"allowNegativeStock\":false}"), "$.id");
        create(token, "/api/v1/companies/" + company + "/branches/" + branch + "/stock/receipts", """
            {"warehouseId":"%s","locationId":"%s","productId":"%s","quantity":5,"unitCode":"PZA","unitCost":10.00,"inventoryStatus":"AVAILABLE"}
            """.formatted(warehouse, location, product));
        List<UUID> balances = jdbc.query("SELECT id FROM inventory.stock_balance WHERE product_id = ?",
            (rs, row) -> UUID.fromString(rs.getString(1)), UUID.fromString(product));
        assertThat(balances).as("la entrada dejó un solo saldo").hasSize(1);
        return balances.get(0);
    }

    private String create(String token, String url, String body) throws Exception {
        return mockMvc.perform(post(url).header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
    }

    private static String token(String company, String branch) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
            .subject("user-uk-1").issuer("jcomercy")
            .claim("companyIds", List.of(company))
            .claim("branchIds", List.of(branch))
            .claim("permissions", "INVENTORY_WRITE INVENTORY_READ")
            .expiration(new Date(System.currentTimeMillis() + 3600_000))
            .signWith(key).compact();
    }
}
