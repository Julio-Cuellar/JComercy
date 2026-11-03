package com.jcode.jcomercy.inventory.infra.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ERR-010: el token de un inicio de sesión real trae permisos con comodines ("*" para el dueño, "inventory.stock.*"
 * para un encargado). Este recorrido pasa por el filtro JWT, el chequeo de permisos y el controlador, como en producción.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WildcardTokenIntegrationTest {

    private static final String COMPANY_ID = "cmp_wildcard_100";
    private static final String BRANCH_ID = "br_wildcard_100";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("El permiso * del dueño abre las rutas de inventario que piden permiso de escritura")
    void ownerTokenWithTheStarOperatesInventory() throws Exception {
        mockMvc.perform(post("/api/v1/companies/{companyId}/branches/{branchId}/warehouses", COMPANY_ID, BRANCH_ID)
                .header("Authorization", tokenWith("*"))
                .header("Idempotency-Key", "idem_wildcard_create_100")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code":"ALM-WILD-01","name":"Almacén comodín","type":"GENERAL",
                     "defaultWarehouse":true,"timezone":"America/Mexico_City","currency":"MXN"}
                    """))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Un encargado con inventory.stock.* consulta el stock, pero no crea almacenes")
    void branchManagerTokenReadsStockButCannotCreateWarehouses() throws Exception {
        String manager = tokenWith("branches.read inventory.stock.* inventory.warehouses.read inventory.products.read");

        mockMvc.perform(get("/api/v1/companies/{companyId}/stock/balances", COMPANY_ID).header("Authorization", manager))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/companies/{companyId}/branches/{branchId}/warehouses", COMPANY_ID, BRANCH_ID)
                .header("Authorization", manager)
                .header("Idempotency-Key", "idem_wildcard_denied_100")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code":"ALM-WILD-02","name":"Almacén negado","type":"GENERAL",
                     "defaultWarehouse":false,"timezone":"America/Mexico_City","currency":"MXN"}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Una cuenta sin permisos no consulta el stock aunque su token nombre la empresa")
    void tokenWithoutPermissionsCannotReadStock() throws Exception {
        mockMvc.perform(get("/api/v1/companies/{companyId}/stock/balances", COMPANY_ID).header("Authorization", tokenWith("")))
            .andExpect(status().isForbidden());
    }

    private static String tokenWith(String permissions) {
        SecretKey key = Keys.hmacShaKeyFor("local-only-change-me-012345678901234567890123456789".getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
            .subject("usuario-wildcard-100")
            .issuer("jcomercy")
            .claim("companyIds", List.of(COMPANY_ID))
            .claim("branchIds", List.of(BRANCH_ID))
            .claim("permissions", permissions)
            .expiration(new Date(System.currentTimeMillis() + 3600_000))
            .signWith(key)
            .compact();
    }
}
