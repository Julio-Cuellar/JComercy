package com.jcode.jcomercy.inventory.infra.security;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ventas aparta, confirma y libera stock con el token de quien vende, y quien vende es personal que no debe poder
 * recibir mercancía. Por eso las rutas de reserva aceptan un permiso angosto, {@code inventory.reservations.write},
 * además de {@code inventory.stock.write}. Ese permiso no abre nada más de inventario.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReservationNarrowPermissionIntegrationTest {

    private static final String COMPANY = "cmp_narrow_100";
    private static final String BRANCH = "br_narrow_100";
    private static final String SECRET = "local-only-change-me-012345678901234567890123456789";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private MockMvc mockMvc;

    private String owner;
    private String seller;
    private String warehouse;
    private String product;

    @BeforeEach
    void stockedProduct() throws Exception {
        owner = tokenWith("*");
        seller = tokenWith("inventory.reservations.write");
        int n = SEQ.incrementAndGet();
        warehouse = JsonPath.read(create(owner, "/api/v1/companies/" + COMPANY + "/branches/" + BRANCH + "/warehouses", """
            {"code":"ALM-NAR-%d","name":"Almacén angosto %d","type":"GENERAL","defaultWarehouse":false,
             "timezone":"America/Mexico_City","currency":"MXN"}
            """.formatted(n, n)), "$.id");
        mockMvc.perform(post("/api/v1/warehouses/{id}/activate", warehouse).header("Authorization", owner)
            .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
        String location = JsonPath.read(create(owner, "/api/v1/warehouses/" + warehouse + "/locations", """
            {"code":"PASILLO-%d","name":"Pasillo %d","type":"INTERNAL"}
            """.formatted(n, n)), "$.id");
        product = JsonPath.read(create(owner, "/api/v1/companies/" + COMPANY + "/products", """
            {"sku":"NAR-SKU-%d","name":"Producto angosto %d","stockUnitCode":"PZA","trackingMode":"NONE",
             "allowNegativeStock":false}
            """.formatted(n, n)), "$.id");
        create(owner, "/api/v1/companies/" + COMPANY + "/branches/" + BRANCH + "/stock/receipts", """
            {"warehouseId":"%s","locationId":"%s","productId":"%s","quantity":10,"unitCode":"PZA",
             "unitCost":10.00,"inventoryStatus":"AVAILABLE"}
            """.formatted(warehouse, location, product));
    }

    @Test
    @DisplayName("Con solo inventory.reservations.write se aparta, se libera y se confirma stock")
    void theNarrowPermissionReservesReleasesAndCommits() throws Exception {
        String reference = "narrow-" + UUID.randomUUID();

        reserve(seller, reference).andExpect(status().isCreated());
        referenceAction(seller, "release", reference).andExpect(status().isOk());
        reserve(seller, reference).andExpect(status().isCreated());
        referenceAction(seller, "commit", reference).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Ese permiso no deja recibir stock ni consultarlo")
    void theNarrowPermissionDoesNotOpenAnythingElse() throws Exception {
        mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/stock/receipts", COMPANY, BRANCH)
                .header("Authorization", seller).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"warehouseId":"%s","locationId":"%s","productId":"%s","quantity":1,"unitCode":"PZA",
                     "unitCost":10.00,"inventoryStatus":"AVAILABLE"}
                    """.formatted(warehouse, UUID.randomUUID(), product)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/companies/{c}/stock/availability", COMPANY).header("Authorization", seller))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Quien solo consulta stock no puede apartarlo")
    void aReaderCannotReserve() throws Exception {
        reserve(tokenWith("inventory.stock.read"), "reader-" + UUID.randomUUID()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El permiso angosto vale solo en las sucursales y empresa de su token")
    void theNarrowPermissionStillRespectsTheTokenScope() throws Exception {
        String otherBranch = tokenWith("inventory.reservations.write", COMPANY, "br_otra_100");

        reserve(otherBranch, "scope-" + UUID.randomUUID()).andExpect(status().isForbidden());
    }

    // ---- Ayudas ----

    private ResultActions reserve(String token, String reference) throws Exception {
        return mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/stock/reservations", COMPANY, BRANCH)
            .header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"warehouseId":"%s","productId":"%s","quantity":2,"unitCode":"PZA",
                 "referenceType":"SALE","referenceId":"%s"}
                """.formatted(warehouse, product, reference)));
    }

    private ResultActions referenceAction(String token, String action, String reference) throws Exception {
        return mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/stock/reservations/" + action, COMPANY, BRANCH)
            .header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"referenceType\":\"SALE\",\"referenceId\":\"%s\"}".formatted(reference)));
    }

    private String create(String token, String url, String body) throws Exception {
        return mockMvc.perform(post(url).header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private static String tokenWith(String permissions) {
        return tokenWith(permissions, COMPANY, BRANCH);
    }

    private static String tokenWith(String permissions, String company, String branch) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
            .subject("usuario-narrow-100")
            .issuer("jcomercy")
            .claim("companyIds", List.of(company))
            .claim("branchIds", List.of(branch))
            .claim("permissions", permissions)
            .expiration(new Date(System.currentTimeMillis() + 3600_000))
            .signWith(key)
            .compact();
    }
}
