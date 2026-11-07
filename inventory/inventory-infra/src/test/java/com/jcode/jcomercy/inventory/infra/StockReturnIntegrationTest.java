package com.jcode.jcomercy.inventory.infra;

import com.jayway.jsonpath.JsonPath;
import com.jcode.jcomercy.inventory.infra.persistence.entity.InventoryOutboxEventJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.InventoryOutboxEventSpringDataRepository;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Devolver stock: lo que salió con una referencia (por ejemplo una venta cancelada) vuelve al mismo saldo, con su costo
 * original, una sola vez. Es lo que usa Ventas para cancelar una venta ya completada.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StockReturnIntegrationTest {

    private static final String COMPANY = "cmp_ret_100";
    private static final String BRANCH = "br_ret_100";
    private static final String OTHER_BRANCH = "br_ret_200";
    private static final String ACTOR = "user-ret-100";
    private static final String SECRET = "local-only-change-me-012345678901234567890123456789";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private InventoryOutboxEventSpringDataRepository outboxRepository;

    private String token;

    @BeforeEach
    void setUp() {
        token = tokenFor(COMPANY, List.of(BRANCH, OTHER_BRANCH), "INVENTORY_WRITE INVENTORY_READ");
    }

    @Test
    @DisplayName("Devolver una reserva confirmada regresa el stock al saldo, con su costo original")
    void returningACommittedReservationGivesTheStockBack() throws Exception {
        var f = fixture("10");
        String reference = "S-" + UUID.randomUUID();
        reserveAndCommit(f, reference, "4");
        assertThat(onHand(f)).isEqualByComparingTo("6");

        returns(token, COMPANY, BRANCH, "SALE", reference, "Venta cancelada")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.returned", hasSize(1)))
                .andExpect(jsonPath("$.returned[0].type", is("RETURN")))
                .andExpect(jsonPath("$.returned[0].quantity", is(4.0)))
                .andExpect(jsonPath("$.returned[0].unitCost", is(10.0)))
                .andExpect(jsonPath("$.returned[0].reason", is("Venta cancelada")))
                .andExpect(jsonPath("$.returned[0].referenceId", is(reference.toUpperCase())));

        assertThat(onHand(f)).isEqualByComparingTo("10");
        assertThat(reserved(f)).isEqualByComparingTo("0");
        mockMvc.perform(get("/api/v1/companies/{c}/stock/movements", COMPANY).param("productId", f.productId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type=='RETURN')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.type=='ISSUE')]", hasSize(1)));
    }

    @Test
    @DisplayName("Una referencia solo se devuelve una vez")
    void aReferenceCanOnlyBeReturnedOnce() throws Exception {
        var f = fixture("10");
        String reference = "S-" + UUID.randomUUID();
        reserveAndCommit(f, reference, "4");
        returns(token, COMPANY, BRANCH, "SALE", reference, "Venta cancelada").andExpect(status().isCreated());

        returns(token, COMPANY, BRANCH, "SALE", reference, "Otra vez").andExpect(status().isConflict());

        assertThat(onHand(f)).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Una referencia que no tiene salidas responde 404")
    void aReferenceWithoutIssuesIsNotFound() throws Exception {
        returns(token, COMPANY, BRANCH, "SALE", "NO-EXISTE-" + UUID.randomUUID(), "Venta cancelada").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Una salida directa con referencia también se devuelve, sin importar mayúsculas")
    void aDirectIssueWithAReferenceCanBeReturnedToo() throws Exception {
        var f = fixture("10");
        String reference = "dir-" + UUID.randomUUID();
        mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/stock/issues", COMPANY, BRANCH)
                        .header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"warehouseId":"%s","locationId":"%s","productId":"%s","quantity":3,"unitCode":"PZA",
                                 "unitCost":10.00,"inventoryStatus":"AVAILABLE","referenceType":"sale","referenceId":"%s"}
                                """.formatted(f.warehouseId, f.locationIds.get(0), f.productId, reference)))
                .andExpect(status().isCreated());
        assertThat(onHand(f)).isEqualByComparingTo("7");

        returns(token, COMPANY, BRANCH, "SALE", reference.toUpperCase(), "Devuelta").andExpect(status().isCreated());

        assertThat(onHand(f)).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Si la salida salió de varios saldos, vuelve a cada uno de ellos")
    void aReturnGoesBackToEveryBalanceItCameFrom() throws Exception {
        var f = fixture("2", "2");
        String reference = "S-" + UUID.randomUUID();
        // Tres unidades: dos salen de un lugar y una del otro.
        reserveAndCommit(f, reference, "3");
        assertThat(onHand(f)).isEqualByComparingTo("1");

        returns(token, COMPANY, BRANCH, "SALE", reference, "Venta cancelada")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.returned", hasSize(2)));

        assertThat(onHand(f)).isEqualByComparingTo("4");
        List<Number> perLocation = JsonPath.read(balances(f), "$[*].onHand");
        assertThat(perLocation).extracting(n -> new BigDecimal(n.toString()))
                .allSatisfy(n -> assertThat(n).isEqualByComparingTo("2"));
    }

    @Test
    @DisplayName("Devolver sube la existencia, así que pide inventory.stock.write: el permiso angosto de reservas no basta")
    void returningNeedsStockWritePermission() throws Exception {
        var f = fixture("10");
        String reference = "S-" + UUID.randomUUID();
        reserveAndCommit(f, reference, "4");

        returns(tokenFor(COMPANY, List.of(BRANCH), "inventory.reservations.write"), COMPANY, BRANCH, "SALE", reference, "x")
                .andExpect(status().isForbidden());
        returns(tokenFor(COMPANY, List.of(BRANCH), "INVENTORY_READ"), COMPANY, BRANCH, "SALE", reference, "x")
                .andExpect(status().isForbidden());
        returns(tokenFor(COMPANY, List.of(OTHER_BRANCH), "INVENTORY_WRITE"), COMPANY, BRANCH, "SALE", reference, "x")
                .andExpect(status().isForbidden());
        returns(tokenFor("cmp_otra_ret", List.of(BRANCH), "INVENTORY_WRITE"), COMPANY, BRANCH, "SALE", reference, "x")
                .andExpect(status().isForbidden());

        assertThat(onHand(f)).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("Cada empresa solo devuelve lo suyo: la referencia de otra empresa no se encuentra")
    void aReferenceOfAnotherCompanyIsNotFound() throws Exception {
        var f = fixture("10");
        String reference = "S-" + UUID.randomUUID();
        reserveAndCommit(f, reference, "4");

        returns(tokenFor("cmp_ret_300", List.of(BRANCH), "INVENTORY_WRITE"), "cmp_ret_300", BRANCH, "SALE", reference, "x")
                .andExpect(status().isNotFound());

        assertThat(onHand(f)).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("No se devuelve en una sucursal que no opera el almacén de donde salió la mercancía")
    void aBranchThatDoesNotOperateTheWarehouseCannotReturnToIt() throws Exception {
        var f = fixture("10");
        String reference = "S-" + UUID.randomUUID();
        reserveAndCommit(f, reference, "4");

        returns(token, COMPANY, OTHER_BRANCH, "SALE", reference, "x").andExpect(status().isConflict());

        assertThat(onHand(f)).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("Con ocho devoluciones de la misma referencia a la vez, solo una se aplica y el stock no se duplica")
    void concurrentReturnsOfTheSameReferenceApplyOnlyOnce() throws Exception {
        var f = fixture("10");
        String reference = "S-" + UUID.randomUUID();
        reserveAndCommit(f, reference, "4");
        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return returns(token, COMPANY, BRANCH, "SALE", reference, "Venta cancelada").andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }

            assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
            assertThat(statuses).filteredOn(code -> code == 409).hasSize(callers - 1);
            assertThat(onHand(f)).isEqualByComparingTo("10");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Devolver deja un evento de movimiento de tipo RETURN en el Outbox")
    void returningPublishesAMovementEvent() throws Exception {
        var f = fixture("10");
        String reference = "S-" + UUID.randomUUID();
        reserveAndCommit(f, reference, "4");

        returns(token, COMPANY, BRANCH, "SALE", reference, "Venta cancelada").andExpect(status().isCreated());

        List<InventoryOutboxEventJpaEntity> events = outboxRepository.findAll().stream()
                .filter(event -> COMPANY.equals(event.getCompanyId()))
                .filter(event -> event.getEventType().equals("inventory.stock.movement"))
                .filter(event -> event.getPayload().contains(f.productId))
                .toList();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getPayload()).contains("RETURN");
        assertThat(events.get(0).getActorUserId()).isEqualTo(ACTOR);
    }

    // ---- ayudas ----

    private record Fixture(String warehouseId, List<String> locationIds, String productId) { }

    /** Un almacén activo con un producto y un lugar por cada cantidad de stock que se indique. */
    private Fixture fixture(String... stockPerLocation) throws Exception {
        int n = SEQ.incrementAndGet();
        String warehouse = JsonPath.read(createOk("/api/v1/companies/" + COMPANY + "/branches/" + BRANCH + "/warehouses", """
                {"code":"ALM-RET-%d","name":"Almacén devoluciones %d","type":"GENERAL","defaultWarehouse":false,
                 "timezone":"America/Mexico_City","currency":"MXN"}
                """.formatted(n, n)), "$.id");
        mockMvc.perform(post("/api/v1/warehouses/{id}/activate", warehouse)
                        .header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
        String product = JsonPath.read(createOk("/api/v1/companies/" + COMPANY + "/products", """
                {"sku":"RET-SKU-%d","name":"Producto devoluciones %d","stockUnitCode":"PZA","trackingMode":"NONE",
                 "allowNegativeStock":false}
                """.formatted(n, n)), "$.id");
        List<String> locations = new ArrayList<>();
        for (int i = 0; i < stockPerLocation.length; i++) {
            String location = JsonPath.read(createOk("/api/v1/warehouses/" + warehouse + "/locations", """
                    {"code":"PASILLO-%d-%d","name":"Pasillo %d %d","type":"INTERNAL"}
                    """.formatted(n, i, n, i)), "$.id");
            createOk("/api/v1/companies/" + COMPANY + "/branches/" + BRANCH + "/stock/receipts", """
                    {"warehouseId":"%s","locationId":"%s","productId":"%s","quantity":%s,"unitCode":"PZA",
                     "unitCost":10.00,"inventoryStatus":"AVAILABLE"}
                    """.formatted(warehouse, location, product, stockPerLocation[i]));
            locations.add(location);
        }
        return new Fixture(warehouse, locations, product);
    }

    private void reserveAndCommit(Fixture f, String reference, String quantity) throws Exception {
        mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/stock/reservations", COMPANY, BRANCH)
                        .header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"warehouseId":"%s","productId":"%s","quantity":%s,"unitCode":"PZA",
                                 "referenceType":"SALE","referenceId":"%s"}
                                """.formatted(f.warehouseId, f.productId, quantity, reference)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/stock/reservations/commit", COMPANY, BRANCH)
                        .header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"referenceType\":\"SALE\",\"referenceId\":\"%s\"}".formatted(reference)))
                .andExpect(status().isOk());
    }

    private ResultActions returns(String bearer, String company, String branch, String referenceType, String referenceId,
                                  String reason) throws Exception {
        return mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/stock/returns", company, branch)
                .header("Authorization", bearer).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"referenceType\":\"%s\",\"referenceId\":\"%s\",\"reason\":\"%s\"}".formatted(referenceType, referenceId, reason)));
    }

    private String createOk(String url, String body) throws Exception {
        return mockMvc.perform(post(url).header("Authorization", token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String balances(Fixture f) throws Exception {
        return mockMvc.perform(get("/api/v1/companies/{c}/stock/balances", COMPANY).param("productId", f.productId)
                        .header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private BigDecimal onHand(Fixture f) throws Exception {
        List<Number> values = JsonPath.read(balances(f), "$[*].onHand");
        return values.stream().map(n -> new BigDecimal(n.toString())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal reserved(Fixture f) throws Exception {
        List<Number> values = JsonPath.read(balances(f), "$[*].reserved");
        return values.stream().map(n -> new BigDecimal(n.toString())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String tokenFor(String companyId, List<String> branches, String permissions) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
                .subject(ACTOR)
                .issuer("jcomercy")
                .claim("companyIds", List.of(companyId))
                .claim("branchIds", branches)
                .claim("permissions", permissions)
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(key)
                .compact();
    }
}
