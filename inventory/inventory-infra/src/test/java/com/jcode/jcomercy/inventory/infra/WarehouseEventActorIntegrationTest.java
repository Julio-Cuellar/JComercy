package com.jcode.jcomercy.inventory.infra;

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

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WarehouseEventActorIntegrationTest {

    private static final String ACTOR = "user-actor-wh-100";
    private static final String COMPANY_ID = "cmp_wh_actor_100";
    private static final String BRANCH_ID = "br_wh_actor_100";

    @Autowired private MockMvc mockMvc;
    @Autowired private InventoryOutboxEventSpringDataRepository outboxRepository;

    private String token;

    @BeforeEach
    void setUp() {
        SecretKey key = Keys.hmacShaKeyFor("local-only-change-me-012345678901234567890123456789".getBytes(StandardCharsets.UTF_8));
        token = "Bearer " + Jwts.builder()
                .subject(ACTOR)
                .issuer("jcomercy")
                .claim("companyIds", List.of(COMPANY_ID))
                .claim("branchIds", List.of(BRANCH_ID))
                .claim("permissions", "INVENTORY_WRITE INVENTORY_READ")
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("Los eventos de almacén registran al usuario autenticado y no 'SYSTEM'")
    void warehouseEventsCarryTheAuthenticatedActor() throws Exception {
        String response = mockMvc.perform(post("/api/v1/companies/{companyId}/branches/{branchId}/warehouses", COMPANY_ID, BRANCH_ID)
                        .header("Authorization", token)
                        .header("Idempotency-Key", "idem_wh_actor_create_100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ALM-ACTOR-01","name":"Almacén actor","type":"GENERAL",
                                 "defaultWarehouse":true,"timezone":"America/Mexico_City","currency":"MXN"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String warehouseId = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        mockMvc.perform(post("/api/v1/warehouses/{warehouseId}/activate", warehouseId)
                        .header("Authorization", token)
                        .header("Idempotency-Key", "idem_wh_actor_activate_100"))
                .andExpect(status().isOk());

        // La base H2 es compartida entre pruebas: se filtra por la empresa propia de esta prueba.
        List<InventoryOutboxEventJpaEntity> own = outboxRepository.findAll().stream()
                .filter(event -> COMPANY_ID.equals(event.getCompanyId()))
                .toList();

        assertThat(own).extracting(InventoryOutboxEventJpaEntity::getEventType)
                .contains("inventory.warehouse.created", "inventory.warehouse.activated");
        assertThat(own).extracting(InventoryOutboxEventJpaEntity::getActorUserId)
                .containsOnly(ACTOR);
    }
}
