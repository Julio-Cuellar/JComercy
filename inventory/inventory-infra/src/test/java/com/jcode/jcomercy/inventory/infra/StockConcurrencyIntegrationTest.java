package com.jcode.jcomercy.inventory.infra;

import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fase P3: el stock con muchas peticiones a la vez. Las pruebas de {@link StockIntegrationTest} son secuenciales, y la
 * única con hilos de inventario es la de dos reservas simultáneas. Aquí se prueba lo que una base de datos real puede
 * tratar distinto que H2: crear el saldo de un producto por primera vez, sacar más de lo que hay, generar seriales para
 * una empresa que aún no tiene secuencia y repetir una misma {@code Idempotency-Key}.
 *
 * <p>Cada prueba usa una empresa nueva, para que los saldos y la secuencia de seriales partan de cero.
 */
@SpringBootTest(properties = "server.port=0")
@AutoConfigureMockMvc
class StockConcurrencyIntegrationTest {

    private static final String BRANCH = "br_conc_100";
    private static final String SECRET = "local-only-change-me-012345678901234567890123456789";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String company;
    private String token;

    @BeforeEach
    void aNewCompany() {
        company = "cmp_conc_" + UUID.randomUUID().toString().substring(0, 8);
        token = tokenFor(company);
    }

    @Test
    @DisplayName("Varias primeras entradas a la vez de un producto dejan un solo saldo con todo lo recibido")
    void firstReceiptsInParallelKeepASingleBalance() throws Exception {
        Fixture f = fixture("NONE");

        List<Integer> statuses = inParallel(8, i -> receive(f, "1", null).andReturn().getResponse().getStatus());

        long created = statuses.stream().filter(status -> status == 201).count();
        // Una entrada posterior sobre el mismo producto y lugar: si quedaron saldos duplicados, no sabe en cuál sumar.
        int afterwards = receive(f, "1", null).andReturn().getResponse().getStatus();

        SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(statuses).allMatch(status -> status < 500);
            soft.assertThat(created).isPositive();
            soft.assertThat(balanceRows(f)).as("saldos del mismo producto y lugar").isEqualTo(1);
            soft.assertThat(onHand(f)).isEqualByComparingTo(BigDecimal.valueOf(created + (afterwards == 201 ? 1 : 0)));
            soft.assertThat(afterwards).as("entrada posterior").isEqualTo(201);
        });
    }

    @Test
    @DisplayName("Varias entradas a la vez sobre un saldo que ya existe suman todas")
    void receiptsInParallelOnAnExistingBalanceAllCount() throws Exception {
        Fixture f = fixture("NONE");
        receive(f, "10", null).andExpect(status().isCreated());

        List<Integer> statuses = inParallel(8, i -> receive(f, "1", null).andReturn().getResponse().getStatus());

        assertThat(statuses).containsOnly(201);
        assertThat(balanceRows(f)).isEqualTo(1);
        assertThat(onHand(f)).isEqualByComparingTo("18");
    }

    @Test
    @DisplayName("Varias salidas a la vez nunca sacan más de lo que hay")
    void issuesInParallelNeverTakeMoreThanThereIs() throws Exception {
        Fixture f = fixture("NONE");
        receive(f, "10", null).andExpect(status().isCreated());

        List<Integer> statuses = inParallel(5, i -> issue(f, "3").andReturn().getResponse().getStatus());

        assertThat(statuses).allMatch(status -> status == 201 || status == 422);
        assertThat(statuses.stream().filter(status -> status == 201).count()).isEqualTo(3);
        assertThat(onHand(f)).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("Los seriales de una empresa nueva, pedidos a la vez en un mismo lugar, son todos distintos y ninguno falla")
    void serialNumbersInParallelAtOneLocationAreAllDifferent() throws Exception {
        Fixture f = fixture("SERIAL");

        assertSerialsAreDistinct(f, List.of(f.locationId, f.locationId, f.locationId, f.locationId, f.locationId, f.locationId));
    }

    @Test
    @DisplayName("Los seriales de una empresa nueva, pedidos a la vez en lugares distintos, son todos distintos y ninguno falla")
    void serialNumbersInParallelAtDifferentLocationsAreAllDifferent() throws Exception {
        // Aquí ningún bloqueo de ubicación pone las peticiones en fila: la secuencia de seriales de la empresa se crea
        // a la vez desde varias transacciones. H2 no ofrece una inserción-si-no-existe atómica y falla en este caso;
        // PostgreSQL, que es la base de producción, sí (ON CONFLICT DO NOTHING).
        Assumptions.assumeTrue("postgres".equals(System.getProperty("jcomercy.test.db")),
            "H2 no tiene inserción atómica sin error; se verifica con -Ppostgres");
        Fixture f = fixture("SERIAL");
        List<String> locations = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            locations.add(JsonPath.read(createOk("/api/v1/warehouses/" + f.warehouseId + "/locations",
                """
                {"code":"SERIE-%d-%d","name":"Ubicación de serie %d","type":"INTERNAL"}
                """.formatted(SEQ.incrementAndGet(), i, i)), "$.id"));
        }

        assertSerialsAreDistinct(f, locations);
    }

    private void assertSerialsAreDistinct(Fixture f, List<String> locations) throws Exception {
        List<Integer> statuses = inParallel(locations.size(), i ->
            receive(new Fixture(f.warehouseId, locations.get(i), f.productId), "1", null).andReturn().getResponse().getStatus());

        assertThat(statuses).containsOnly(201);
        List<String> serials = jdbc.queryForList(
            "SELECT serial_number FROM inventory.stock_balance WHERE product_id=?", String.class, UUID.fromString(f.productId));
        assertThat(serials).hasSize(locations.size()).doesNotHaveDuplicates().doesNotContainNull();
    }

    @Test
    @DisplayName("La misma Idempotency-Key repetida a la vez registra el movimiento una sola vez y no falla")
    void theSameIdempotencyKeyInParallelRunsOnce() throws Exception {
        Fixture f = fixture("NONE");
        String key = "idem-conc-" + UUID.randomUUID();

        List<Integer> statuses = inParallel(6, i -> receive(f, "5", key).andReturn().getResponse().getStatus());

        // Quien llega mientras la primera aún corre recibe un conflicto; quien llega después, la misma respuesta.
        assertThat(statuses).allMatch(status -> status == 201 || status == 409);
        assertThat(statuses).contains(201);
        assertThat(movementRows(f)).isEqualTo(1);
        assertThat(onHand(f)).isEqualByComparingTo("5");
    }

    // ---- Ayudas ----

    private record Fixture(String warehouseId, String locationId, String productId) { }

    private Fixture fixture(String trackingMode) throws Exception {
        int n = SEQ.incrementAndGet();
        String warehouse = JsonPath.read(createOk("/api/v1/companies/" + company + "/branches/" + BRANCH + "/warehouses",
            """
            {"code":"ALM-CONC-%d","name":"Almacén concurrencia %d","type":"GENERAL","defaultWarehouse":false,
             "timezone":"America/Mexico_City","currency":"MXN"}
            """.formatted(n, n)), "$.id");
        mockMvc.perform(post("/api/v1/warehouses/{id}/activate", warehouse)
                .header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString()))
            .andExpect(status().isOk());
        String location = JsonPath.read(createOk("/api/v1/warehouses/" + warehouse + "/locations",
            """
            {"code":"PASILLO-%d","name":"Ubicación %d","type":"INTERNAL"}
            """.formatted(n, n)), "$.id");
        String product = JsonPath.read(createOk("/api/v1/companies/" + company + "/products",
            """
            {"sku":"CONC-SKU-%d","name":"Producto concurrencia %d","stockUnitCode":"PZA","trackingMode":"%s",
             "allowNegativeStock":false}
            """.formatted(n, n, trackingMode)), "$.id");
        return new Fixture(warehouse, location, product);
    }

    private ResultActions receive(Fixture f, String quantity, String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/stock/receipts", company, BRANCH)
            .header("Authorization", token)
            .header("Idempotency-Key", idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"warehouseId":"%s","locationId":"%s","productId":"%s","quantity":%s,"unitCode":"PZA",
                 "unitCost":10.00,"inventoryStatus":"AVAILABLE"}
                """.formatted(f.warehouseId, f.locationId, f.productId, quantity)));
    }

    private ResultActions issue(Fixture f, String quantity) throws Exception {
        return mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/stock/issues", company, BRANCH)
            .header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"warehouseId":"%s","locationId":"%s","productId":"%s","quantity":%s,"unitCode":"PZA",
                 "inventoryStatus":"AVAILABLE"}
                """.formatted(f.warehouseId, f.locationId, f.productId, quantity)));
    }

    private String createOk(String url, String body) throws Exception {
        return mockMvc.perform(post(url).header("Authorization", token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
    }

    private int balanceRows(Fixture f) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM inventory.stock_balance WHERE product_id=?",
            Integer.class, UUID.fromString(f.productId));
    }

    private BigDecimal onHand(Fixture f) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(on_hand), 0) FROM inventory.stock_balance WHERE product_id=?",
            BigDecimal.class, UUID.fromString(f.productId));
    }

    private int movementRows(Fixture f) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM inventory.inventory_movement WHERE product_id=?",
            Integer.class, UUID.fromString(f.productId));
    }

    private static String tokenFor(String companyId) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
            .subject("user-conc-100")
            .issuer("jcomercy")
            .claim("companyIds", List.of(companyId))
            .claim("branchIds", List.of(BRANCH))
            .claim("permissions", "INVENTORY_WRITE INVENTORY_READ")
            .expiration(new Date(System.currentTimeMillis() + 3600_000))
            .signWith(key)
            .compact();
    }

    /** Lanza {@code count} tareas a la vez, todas esperando la misma señal de salida, y devuelve lo que cada una respondió. */
    private <T> List<T> inParallel(int count, Attempt<T> attempt) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            List<Future<T>> pending = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int number = i;
                Callable<T> task = () -> {
                    start.await();
                    return attempt.run(number);
                };
                pending.add(pool.submit(task));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> one : pending) {
                results.add(one.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface Attempt<T> {
        T run(int number) throws Exception;
    }
}
