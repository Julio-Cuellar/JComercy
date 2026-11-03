package com.jcode.jcomercy.inventory.infra;

import com.jayway.jsonpath.JsonPath;
import com.jcode.jcomercy.inventory.application.service.ReservationApplicationService;
import com.jcode.jcomercy.inventory.infra.persistence.entity.InventoryOutboxEventJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.InventoryOutboxEventSpringDataRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
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
import java.time.Instant;
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

@SpringBootTest
@AutoConfigureMockMvc
class ReservationIntegrationTest {

    private static final String COMPANY = "cmp_res_100";
    private static final String BRANCH = "br_res_100";
    private static final String ACTOR = "user-res-100";
    private static final String SECRET = "local-only-change-me-012345678901234567890123456789";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private InventoryOutboxEventSpringDataRepository outboxRepository;
    @Autowired private ReservationApplicationService reservationService;

    private String token;

    @BeforeEach
    void setUp() {
        token = tokenFor(COMPANY, "INVENTORY_WRITE INVENTORY_READ");
    }

    @Test
    @DisplayName("Reservar aparta stock sin sacarlo del almacén")
    void reservingSetsStockAsideWithoutRemovingIt() throws Exception {
        var f = fixture("10");

        reserve(f, "ML-1-1", "4.0")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservations.length()", is(1)))
                .andExpect(jsonPath("$.reservations[0].status", is("ACTIVE")))
                .andExpect(jsonPath("$.reservations[0].quantity", is(4.0)))
                .andExpect(jsonPath("$.reservations[0].referenceId", is("ML-1-1")))
                .andExpect(jsonPath("$.available", is(6.0)));

        mockMvc.perform(get("/api/v1/companies/{c}/stock/balances", COMPANY)
                        .param("productId", f.productId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].onHand", is(10.0)))
                .andExpect(jsonPath("$[0].reserved", is(4.0)));
        assertThat(availableFor(f)).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("No se puede reservar más de lo disponible")
    void cannotReserveMoreThanAvailable() throws Exception {
        var f = fixture("10");
        reserve(f, "ML-2-1", "4").andExpect(status().isCreated());

        reserve(f, "ML-2-2", "7").andExpect(status().isUnprocessableEntity());

        assertThat(availableFor(f)).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("La misma referencia no se puede reservar dos veces")
    void sameReferenceCannotBeReservedTwice() throws Exception {
        var f = fixture("10");
        reserve(f, "ML-3-1", "4").andExpect(status().isCreated());

        reserve(f, "ml-3-1", "4").andExpect(status().isConflict());

        assertThat(availableFor(f)).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("Liberar devuelve el stock y solo se puede hacer una vez")
    void releasingReturnsStockAndIsOnlyPossibleOnce() throws Exception {
        var f = fixture("10");
        reserve(f, "ML-4-1", "4").andExpect(status().isCreated());

        release("ML-4-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations[0].status", is("RELEASED")))
                .andExpect(jsonPath("$.available", is(10.0)));

        release("ML-4-1").andExpect(status().isNotFound());
        assertThat(availableFor(f)).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Confirmar descuenta el stock y deja el movimiento en el kárdex")
    void committingShipsTheStockAndWritesTheKardex() throws Exception {
        var f = fixture("10");
        reserve(f, "ML-5-1", "4").andExpect(status().isCreated());

        commit("ML-5-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations[0].status", is("COMMITTED")))
                .andExpect(jsonPath("$.movements.length()", is(1)))
                .andExpect(jsonPath("$.movements[0].type", is("ISSUE")))
                .andExpect(jsonPath("$.movements[0].referenceType", is("ORDER_LINE")))
                .andExpect(jsonPath("$.movements[0].referenceId", is("ML-5-1")))
                .andExpect(jsonPath("$.available", is(6.0)));

        mockMvc.perform(get("/api/v1/companies/{c}/stock/balances", COMPANY)
                        .param("productId", f.productId).header("Authorization", token))
                .andExpect(jsonPath("$[0].onHand", is(6.0)))
                .andExpect(jsonPath("$[0].reserved", is(0.0)));
        mockMvc.perform(get("/api/v1/companies/{c}/stock/movements", COMPANY)
                        .param("productId", f.productId).header("Authorization", token))
                .andExpect(jsonPath("$[?(@.type=='ISSUE' && @.referenceId=='ML-5-1')]", hasSize(1)));

        commit("ML-5-1").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Si ninguna ubicación alcanza sola, la reserva se reparte entre varias")
    void reservationSpansSeveralLocationsWhenNoSingleOneIsEnough() throws Exception {
        var f = fixture("3");
        String secondLocation = createLocation(f.warehouseId, "PASILLO-B");
        receive(f.warehouseId, secondLocation, f.productId, "5");

        String response = reserve(f, "ML-6-1", "6")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservations.length()", is(2)))
                .andExpect(jsonPath("$.available", is(2.0)))
                .andReturn().getResponse().getContentAsString();

        List<Number> quantities = JsonPath.read(response, "$.reservations[*].quantity");
        assertThat(quantities.stream().map(n -> new BigDecimal(n.toString())).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("6");

        commit("ML-6-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movements.length()", is(2)))
                .andExpect(jsonPath("$.available", is(2.0)));
    }

    @RepeatedTest(10)
    @DisplayName("Con dos reservas simultáneas solo cabe una")
    void onlyOneOfTwoConcurrentReservationsFitsInTheStock() throws Exception {
        var f = fixture("10");
        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            // Referencias propias de esta repetición: las reservas activas de otra no deben estorbar (ver ERR-004).
            String suffix = f.productId().substring(0, 8);
            for (String reference : List.of("ML-7-A-" + suffix, "ML-7-B-" + suffix)) {
                results.add(pool.submit(() -> {
                    start.await();
                    return reserve(f, reference, "6").andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) statuses.add(result.get());

            assertThat(statuses).containsExactlyInAnyOrder(201, 422);
        } finally {
            pool.shutdownNow();
        }
        assertThat(availableFor(f)).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("Exige permiso de escritura de stock y acceso a la empresa")
    void requiresStockWritePermissionAndCompanyAccess() throws Exception {
        var f = fixture("10");
        String readOnly = tokenFor(COMPANY, "INVENTORY_READ");
        String otherCompany = tokenFor("cmp_res_other", "INVENTORY_WRITE INVENTORY_READ");

        for (String denied : List.of(readOnly, otherCompany)) {
            mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/stock/reservations", COMPANY, BRANCH)
                            .header("Authorization", denied)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reservationJson(f, "ML-8-1", "1")))
                    .andExpect(status().isForbidden());
        }
        assertThat(availableFor(f)).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("El ciclo de la reserva se publica en el Outbox con el usuario real")
    void reservationLifecycleIsPublishedInTheOutbox() throws Exception {
        var f = fixture("10");
        reserve(f, "ML-9-A", "2").andExpect(status().isCreated());
        reserve(f, "ML-9-B", "3").andExpect(status().isCreated());
        release("ML-9-B").andExpect(status().isOk());
        commit("ML-9-A").andExpect(status().isOk());

        List<InventoryOutboxEventJpaEntity> own = outboxRepository.findAll().stream()
                .filter(event -> COMPANY.equals(event.getCompanyId()))
                .filter(event -> event.getPayload().contains(f.productId))
                .toList();

        assertThat(own).extracting(InventoryOutboxEventJpaEntity::getEventType)
                .filteredOn(type -> type.startsWith("inventory.reservation."))
                .containsExactlyInAnyOrder("inventory.reservation.created", "inventory.reservation.created",
                        "inventory.reservation.released", "inventory.reservation.committed");
        assertThat(own).extracting(InventoryOutboxEventJpaEntity::getEventType).contains("inventory.stock.issued");
        assertThat(own).filteredOn(event -> event.getEventType().startsWith("inventory.reservation."))
                .extracting(InventoryOutboxEventJpaEntity::getActorUserId).containsOnly(ACTOR);
        assertThat(own).filteredOn(event -> event.getEventType().equals("inventory.reservation.created"))
                .allSatisfy(event -> assertThat(event.getPayload()).contains("\"available\""));
    }

    @Test
    @DisplayName("Una reserva vencida devuelve el stock y ya no se puede confirmar")
    void expiredReservationGivesTheStockBackAndCannotBeCommitted() throws Exception {
        var f = fixture("10");
        reserveWithDeadline(f, "ML-10-1", "4", Instant.now().plusSeconds(600))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservations[0].expiresAt").exists());
        reserve(f, "ML-10-2", "3").andExpect(status().isCreated());

        assertThat(reservationService.expireDue(Instant.now(), 100)).isZero();
        assertThat(availableFor(f)).isEqualByComparingTo("3");

        assertThat(reservationService.expireDue(Instant.now().plusSeconds(3600), 100)).isEqualTo(1);
        assertThat(availableFor(f)).isEqualByComparingTo("7");

        commit("ML-10-1").andExpect(status().isNotFound());
        commit("ML-10-2").andExpect(status().isOk());

        List<InventoryOutboxEventJpaEntity> expired = outboxRepository.findAll().stream()
                .filter(event -> COMPANY.equals(event.getCompanyId()))
                .filter(event -> event.getEventType().equals("inventory.reservation.expired"))
                .filter(event -> event.getPayload().contains(f.productId))
                .toList();
        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).getActorUserId()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("Un plazo que ya pasó se rechaza al reservar")
    void deadlineInThePastIsRejected() throws Exception {
        var f = fixture("10");

        reserveWithDeadline(f, "ML-11-1", "4", Instant.now().minusSeconds(60))
                .andExpect(status().isUnprocessableEntity());

        assertThat(availableFor(f)).isEqualByComparingTo("10");
    }

    // ---- ayudas ----

    private ResultActions reserveWithDeadline(Fixture f, String reference, String quantity, Instant expiresAt) throws Exception {
        return mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/stock/reservations", COMPANY, BRANCH)
                .header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"warehouseId":"%s","productId":"%s","quantity":%s,"unitCode":"PZA",
                         "referenceType":"ORDER_LINE","referenceId":"%s","expiresAt":"%s"}
                        """.formatted(f.warehouseId, f.productId, quantity, reference, expiresAt)));
    }

    private record Fixture(String warehouseId, String locationId, String productId) { }

    private Fixture fixture(String stock) throws Exception {
        int n = SEQ.incrementAndGet();
        String warehouse = JsonPath.read(createOk("/api/v1/companies/" + COMPANY + "/branches/" + BRANCH + "/warehouses",
                """
                {"code":"ALM-RES-%d","name":"Almacén reservas %d","type":"GENERAL","defaultWarehouse":false,
                 "timezone":"America/Mexico_City","currency":"MXN"}
                """.formatted(n, n)), "$.id");
        mockMvc.perform(post("/api/v1/warehouses/{id}/activate", warehouse)
                        .header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
        String location = createLocation(warehouse, "PASILLO-A");
        String product = JsonPath.read(createOk("/api/v1/companies/" + COMPANY + "/products",
                """
                {"sku":"RES-SKU-%d","name":"Producto reservas %d","stockUnitCode":"PZA","trackingMode":"NONE",
                 "allowNegativeStock":false}
                """.formatted(n, n)), "$.id");
        receive(warehouse, location, product, stock);
        return new Fixture(warehouse, location, product);
    }

    private String createLocation(String warehouseId, String code) throws Exception {
        return JsonPath.read(createOk("/api/v1/warehouses/" + warehouseId + "/locations",
                """
                {"code":"%s","name":"Ubicación %s","type":"INTERNAL"}
                """.formatted(code, code)), "$.id");
    }

    private void receive(String warehouseId, String locationId, String productId, String quantity) throws Exception {
        createOk("/api/v1/companies/" + COMPANY + "/branches/" + BRANCH + "/stock/receipts",
                """
                {"warehouseId":"%s","locationId":"%s","productId":"%s","quantity":%s,"unitCode":"PZA",
                 "unitCost":10.00,"inventoryStatus":"AVAILABLE"}
                """.formatted(warehouseId, locationId, productId, quantity));
    }

    private String createOk(String url, String body) throws Exception {
        return mockMvc.perform(post(url).header("Authorization", token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private static String reservationJson(Fixture f, String reference, String quantity) {
        return """
                {"warehouseId":"%s","productId":"%s","quantity":%s,"unitCode":"PZA",
                 "referenceType":"ORDER_LINE","referenceId":"%s"}
                """.formatted(f.warehouseId, f.productId, quantity, reference);
    }

    private ResultActions reserve(Fixture f, String reference, String quantity) throws Exception {
        return mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/stock/reservations", COMPANY, BRANCH)
                .header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(reservationJson(f, reference, quantity)));
    }

    private ResultActions release(String reference) throws Exception {
        return referenceAction("release", reference);
    }

    private ResultActions commit(String reference) throws Exception {
        return referenceAction("commit", reference);
    }

    private ResultActions referenceAction(String action, String reference) throws Exception {
        return mockMvc.perform(post("/api/v1/companies/{c}/branches/{b}/stock/reservations/" + action, COMPANY, BRANCH)
                .header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"referenceType\":\"ORDER_LINE\",\"referenceId\":\"%s\"}".formatted(reference)));
    }

    private BigDecimal availableFor(Fixture f) throws Exception {
        String body = mockMvc.perform(get("/api/v1/companies/{c}/stock/availability", COMPANY)
                        .param("productId", f.productId).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> values = JsonPath.read(body, "$[?(@.warehouseId=='" + f.warehouseId + "')].available");
        return new BigDecimal(values.get(0).toString());
    }

    private static String tokenFor(String companyId, String permissions) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
                .subject(ACTOR)
                .issuer("jcomercy")
                .claim("companyIds", List.of(companyId))
                .claim("branchIds", List.of(BRANCH))
                .claim("permissions", permissions)
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(key)
                .compact();
    }
}
