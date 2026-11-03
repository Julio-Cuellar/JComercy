package com.jcode.jcomercy.inventory.infra.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La disponibilidad (existencia menos reservado) también se consulta por GraphQL, con el mismo alcance que por REST. */
@SpringBootTest
@AutoConfigureMockMvc
class GraphQlAvailabilityIntegrationTest {

    private static final String COMPANY = "cmp_gql_avail_100";
    private static final String OTHER_COMPANY = "cmp_gql_avail_200";
    private static final String BRANCH = "br_gql_avail_100";
    private static final String SECRET = "local-only-change-me-012345678901234567890123456789";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Muestra existencia, reservado y disponible del producto")
    void showsOnHandReservedAndAvailable() throws Exception {
        Fixture fixture = fixtureWithReservation("10", "4");

        availability(tokenFor(COMPANY, "INVENTORY_READ"), COMPANY, fixture.productId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors").doesNotExist())
            .andExpect(jsonPath("$.data.stockAvailability", hasSize(1)))
            .andExpect(jsonPath("$.data.stockAvailability[0].warehouseId", is(fixture.warehouseId)))
            .andExpect(jsonPath("$.data.stockAvailability[0].productId", is(fixture.productId)))
            .andExpect(jsonPath("$.data.stockAvailability[0].onHand", is(10.0)))
            .andExpect(jsonPath("$.data.stockAvailability[0].reserved", is(4.0)))
            .andExpect(jsonPath("$.data.stockAvailability[0].available", is(6.0)));
    }

    @Test
    @DisplayName("Quien no tiene acceso a la empresa recibe una lista vacía")
    void anotherCompanyGetsAnEmptyList() throws Exception {
        Fixture fixture = fixtureWithReservation("10", "4");

        availability(tokenFor(OTHER_COMPANY, "INVENTORY_READ"), COMPANY, fixture.productId)
            .andExpect(jsonPath("$.errors").doesNotExist())
            .andExpect(jsonPath("$.data.stockAvailability", hasSize(0)));
    }

    @Test
    @DisplayName("Quien no tiene permiso de lectura de stock recibe una lista vacía")
    void withoutReadPermissionGetsAnEmptyList() throws Exception {
        Fixture fixture = fixtureWithReservation("10", "4");

        availability(tokenFor(COMPANY, ""), COMPANY, fixture.productId)
            .andExpect(jsonPath("$.errors").doesNotExist())
            .andExpect(jsonPath("$.data.stockAvailability", hasSize(0)));
    }

    private ResultActions availability(String authorization, String companyId, String productId) throws Exception {
        String document = new String(
            new ClassPathResource("graphql/operations/StockAvailability.graphql").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        Map<String, Object> body = Map.of("query", document, "variables", Map.of("companyId", companyId, "productId", productId));
        ResultActions result = mockMvc.perform(post("/graphql/v1").contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", authorization).content(objectMapper.writeValueAsString(body)));
        return result.andReturn().getRequest().isAsyncStarted() ? mockMvc.perform(asyncDispatch(result.andReturn())) : result;
    }

    private record Fixture(String warehouseId, String productId) { }

    private Fixture fixtureWithReservation(String stock, String reserved) throws Exception {
        String writer = tokenFor(COMPANY, "INVENTORY_WRITE INVENTORY_READ");
        int n = SEQ.incrementAndGet() + (int) (System.nanoTime() % 100_000);
        String warehouse = JsonPath.read(createOk(writer, "/api/v1/companies/" + COMPANY + "/branches/" + BRANCH + "/warehouses",
            """
            {"code":"ALM-GQL-%d","name":"Almacén GraphQL %d","type":"GENERAL","defaultWarehouse":false,
             "timezone":"America/Mexico_City","currency":"MXN"}
            """.formatted(n, n)), "$.id");
        mockMvc.perform(post("/api/v1/warehouses/{id}/activate", warehouse)
                .header("Authorization", writer).header("Idempotency-Key", UUID.randomUUID().toString()))
            .andExpect(status().isOk());
        String location = JsonPath.read(createOk(writer, "/api/v1/warehouses/" + warehouse + "/locations",
            "{\"code\":\"PASILLO-%d\",\"name\":\"Pasillo %d\",\"type\":\"INTERNAL\"}".formatted(n, n)), "$.id");
        String product = JsonPath.read(createOk(writer, "/api/v1/companies/" + COMPANY + "/products",
            """
            {"sku":"GQL-SKU-%d","name":"Producto GraphQL %d","stockUnitCode":"PZA","trackingMode":"NONE",
             "allowNegativeStock":false}
            """.formatted(n, n)), "$.id");
        createOk(writer, "/api/v1/companies/" + COMPANY + "/branches/" + BRANCH + "/stock/receipts",
            """
            {"warehouseId":"%s","locationId":"%s","productId":"%s","quantity":%s,"unitCode":"PZA",
             "unitCost":10.00,"inventoryStatus":"AVAILABLE"}
            """.formatted(warehouse, location, product, stock));
        createOk(writer, "/api/v1/companies/" + COMPANY + "/branches/" + BRANCH + "/stock/reservations",
            """
            {"warehouseId":"%s","productId":"%s","quantity":%s,"unitCode":"PZA",
             "referenceType":"ORDER_LINE","referenceId":"GQL-%d"}
            """.formatted(warehouse, product, reserved, n));
        return new Fixture(warehouse, product);
    }

    private String createOk(String authorization, String url, String body) throws Exception {
        return mockMvc.perform(post(url).header("Authorization", authorization)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
    }

    private static String tokenFor(String companyId, String permissions) {
        return "Bearer " + Jwts.builder()
            .subject("user-gql-avail")
            .issuer("jcomercy")
            .claim("companyIds", List.of(companyId))
            .claim("branchIds", List.of(BRANCH))
            .claim("permissions", permissions)
            .expiration(new Date(System.currentTimeMillis() + 3600_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }
}
