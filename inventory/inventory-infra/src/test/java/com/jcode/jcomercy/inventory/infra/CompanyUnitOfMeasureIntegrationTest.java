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
class CompanyUnitOfMeasureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String token;
    private final String companyId = "cmp_test_units_100";

    @BeforeEach
    void setUp() {
        SecretKey key = Keys.hmacShaKeyFor("local-only-change-me-012345678901234567890123456789".getBytes(StandardCharsets.UTF_8));
        token = "Bearer " + Jwts.builder()
                .subject("user-test-01")
                .issuer("jcomercy")
                .claim("companyIds", List.of(companyId))
                .claim("branchIds", List.of())
                .claim("permissions", "INVENTORY_WRITE INVENTORY_READ")
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("Debe crear una unidad de empaque propia, listarla junto a las globales y usarla en alta de productos y conversiones")
    void shouldCreateAndUseCompanyUnitOfMeasure() throws Exception {
        // 1. Crear unidad propia CAJA para la empresa
        String createUnitJson = """
            {
                "code": "CAJA_12",
                "name": "Caja con 12 piezas",
                "symbol": "cj12",
                "dimension": "COUNT",
                "baseUnitCode": "PZA",
                "conversionFactor": 12.0,
                "decimals": 0
            }
            """;

        mockMvc.perform(post("/api/v1/companies/{companyId}/units-of-measure", companyId)
                .header("Authorization", token)
                .header("Idempotency-Key", "idem_unit_001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createUnitJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is("CAJA_12")))
                .andExpect(jsonPath("$.name", is("Caja con 12 piezas")))
                .andExpect(jsonPath("$.baseUnitCode", is("PZA")))
                .andExpect(jsonPath("$.conversionFactor", is(12.0)));

        // 2. Listar catálogo unificado (globales + propias)
        mockMvc.perform(get("/api/v1/companies/{companyId}/units-of-measure", companyId)
                .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'PZA')].system", hasItem(true)))
                .andExpect(jsonPath("$[?(@.code == 'CAJA_12')].system", hasItem(false)));

        // 3. Crear un producto utilizando la unidad de empaque propia en purchaseUnitCode
        String createProductJson = """
            {
                "sku": "PROD-EMP-001",
                "name": "Producto prueba empaque",
                "stockUnitCode": "PZA",
                "purchaseUnitCode": "CAJA_12",
                "saleUnitCode": "PZA",
                "trackingMode": "NONE",
                "allowNegativeStock": false
            }
            """;

        String productResponseBody = mockMvc.perform(post("/api/v1/companies/{companyId}/products", companyId)
                .header("Authorization", token)
                .header("Idempotency-Key", "idem_prod_001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createProductJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku", is("PROD-EMP-001")))
                .andExpect(jsonPath("$.purchaseUnitCode", is("CAJA_12")))
                .andReturn().getResponse().getContentAsString();

        // Extraer id del producto creado para agregar una conversión
        String productIdStr = com.jayway.jsonpath.JsonPath.read(productResponseBody, "$.id");

        // 4. Registrar una conversión de unidad para el producto usando la unidad de empaque propia
        String addConversionJson = """
            {
                "fromUnitCode": "CAJA_12",
                "toUnitCode": "PZA",
                "factor": 12.0,
                "roundingScale": 3,
                "roundingMode": "HALF_UP",
                "purchaseUnit": true,
                "salesUnit": false,
                "defaultConversion": true
            }
            """;

        mockMvc.perform(post("/api/v1/companies/{companyId}/products/{productId}/unit-conversions", companyId, productIdStr)
                .header("Authorization", token)
                .header("Idempotency-Key", "idem_conv_001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addConversionJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromUnitCode", is("CAJA_12")))
                .andExpect(jsonPath("$.toUnitCode", is("PZA")))
                .andExpect(jsonPath("$.factor", is(12.0)));
    }

    @Test
    @DisplayName("Debe rechazar la creación de una unidad propia con código duplicado")
    void shouldRejectDuplicateCompanyUnitCode() throws Exception {
        String createUnitJson = """
            {
                "code": "CAJA_DUP",
                "name": "Caja Duplicada",
                "symbol": "cjdup",
                "dimension": "COUNT",
                "baseUnitCode": "PZA",
                "conversionFactor": 6.0,
                "decimals": 0
            }
            """;

        mockMvc.perform(post("/api/v1/companies/{companyId}/units-of-measure", companyId)
                .header("Authorization", token)
                .header("Idempotency-Key", "idem_unit_dup_1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createUnitJson))
                .andExpect(status().isCreated());

        // Intentar crear la misma unidad nuevamente
        mockMvc.perform(post("/api/v1/companies/{companyId}/units-of-measure", companyId)
                .header("Authorization", token)
                .header("Idempotency-Key", "idem_unit_dup_2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createUnitJson))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Debe rechazar la creación de una unidad propia con dimensión distinta a la unidad base")
    void shouldRejectDimensionMismatchWithBaseUnit() throws Exception {
        String createUnitJson = """
            {
                "code": "CAJA_MISMATCH",
                "name": "Caja Mismatch",
                "symbol": "cjm",
                "dimension": "MASS",
                "baseUnitCode": "PZA",
                "conversionFactor": 10.0,
                "decimals": 0
            }
            """;

        mockMvc.perform(post("/api/v1/companies/{companyId}/units-of-measure", companyId)
                .header("Authorization", token)
                .header("Idempotency-Key", "idem_unit_mismatch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createUnitJson))
                .andExpect(status().isConflict());
    }
}
