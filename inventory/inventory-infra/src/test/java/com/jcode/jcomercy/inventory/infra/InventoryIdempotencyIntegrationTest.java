package com.jcode.jcomercy.inventory.infra;

import com.jcode.jcomercy.inventory.infra.persistence.repository.InventoryIdempotencyKeySpringDataRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
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
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "server.port=0")
@AutoConfigureMockMvc
class InventoryIdempotencyIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private InventoryIdempotencyKeySpringDataRepository idempotencyRepository;

    private String createToken(String companyId, String branchId) {
        SecretKey key = Keys.hmacShaKeyFor("local-only-change-me-012345678901234567890123456789".getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
                .subject("user-idem-test")
                .issuer("jcomercy")
                .claim("companyIds", List.of(companyId))
                .claim("branchIds", List.of(branchId))
                .claim("permissions", "INVENTORY_WRITE INVENTORY_READ")
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(key)
                .compact();
    }

    @BeforeEach
    void setUp() {
        idempotencyRepository.deleteAll();
    }

    @Test
    void identicalIdempotentRequestReturnsCachedResponseAndDoesNotReexecute() throws Exception {
        String companyId = "cmp_idem_100";
        String branchId = "br_idem_100";
        String token = createToken(companyId, branchId);
        String idempotencyKey = "idem_key_unique_001";

        String warehouseJson = """
            {
                "scope": "BRANCH_OWNED",
                "code": "WH-IDEM-001",
                "name": "Almacén Idempotente",
                "type": "GENERAL",
                "defaultWarehouse": false,
                "timezone": "America/Mexico_City",
                "currency": "MXN"
            }
            """;

        // First call: 201 Created
        var result1 = mockMvc.perform(post("/api/v1/companies/{companyId}/branches/{branchId}/warehouses", companyId, branchId)
                .header("Authorization", token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(warehouseJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is("WH-IDEM-001")))
                .andReturn();

        String response1Str = result1.getResponse().getContentAsString();

        // Second call with identical key & body: returns cached 201 Created response
        var result2 = mockMvc.perform(post("/api/v1/companies/{companyId}/branches/{branchId}/warehouses", companyId, branchId)
                .header("Authorization", token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(warehouseJson))
                .andExpect(status().isCreated())
                .andReturn();

        String response2Str = result2.getResponse().getContentAsString();

        assertThat(response2Str).isEqualTo(response1Str);
        assertThat(idempotencyRepository.count()).isEqualTo(1);
    }

    @Test
    void reusedKeyWithDifferentPayloadReturnsConflict() throws Exception {
        String companyId = "cmp_idem_200";
        String branchId = "br_idem_200";
        String token = createToken(companyId, branchId);
        String idempotencyKey = "idem_key_conflict_002";

        String warehouseJson1 = """
            {
                "scope": "BRANCH_OWNED",
                "code": "WH-IDEM-002A",
                "name": "Almacén Original",
                "type": "GENERAL",
                "defaultWarehouse": false,
                "timezone": "America/Mexico_City",
                "currency": "MXN"
            }
            """;

        String warehouseJson2 = """
            {
                "scope": "BRANCH_OWNED",
                "code": "WH-IDEM-002B",
                "name": "Almacén Diferente",
                "type": "GENERAL",
                "defaultWarehouse": false,
                "timezone": "America/Mexico_City",
                "currency": "MXN"
            }
            """;

        // First call: succeeds
        mockMvc.perform(post("/api/v1/companies/{companyId}/branches/{branchId}/warehouses", companyId, branchId)
                .header("Authorization", token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(warehouseJson1))
                .andExpect(status().isCreated());

        // Second call with SAME key but DIFFERENT payload: 409 Conflict
        mockMvc.perform(post("/api/v1/companies/{companyId}/branches/{branchId}/warehouses", companyId, branchId)
                .header("Authorization", token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(warehouseJson2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("IDEMPOTENCY_KEY_CONFLICT")));
    }
}
