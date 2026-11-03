package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El montaje que comparten las pruebas de acceso a empresas: cuentas reales con inicio de sesión real, empresas y
 * sucursales creadas por la API, y membresías insertadas por SQL porque ninguna ruta las crea todavía.
 */
final class RestWorld {

    static final String PASSWORD = "ClaveSegura123!";
    private static final AtomicInteger SEQ = new AtomicInteger(900_000);

    record Person(String accountId, String email) { }

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    RestWorld(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    static int next() {
        return SEQ.incrementAndGet();
    }

    Person register() throws Exception {
        int n = next();
        String email = "acceso-%d@example.com".formatted(n);
        String body = """
            {"name":"Cuenta %d","owner":{"firstName":"Persona","lastName":"Prueba","birthDate":"1985-04-12",
            "rfc":"ACC%010d","phoneNumber":"5512345678","email":"%s",
            "password":"%s","state":"Ciudad de México","address":"Av. Reforma 100"}}
            """.formatted(n, n, email, PASSWORD);
        String response = mockMvc.perform(post("/api/v1/accounts/registration")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return new Person(objectMapper.readTree(response).get("publicAccountId").asText(), email);
    }

    /** El token del inicio de sesión, con el prefijo "Bearer " listo para el encabezado. */
    String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("accessToken").asText();
    }

    String createCompany(String accountId, String bearer) throws Exception {
        int n = next();
        String body = """
            {"legalName":"Empresa %d S.A.","tradeName":"Empresa %d","rfc":"RFC%010d","taxRegime":"601",
            "incorporationDate":"2020-01-15","phone":"5512345678","email":"empresa-%d@example.com",
            "website":"https://example.com","logoUrl":"https://example.com/logo.png",
            "fiscalAddress":{"calle":"Reforma","numeroExterior":"100","numeroInterior":"10","colonia":"Juárez",
            "codigoPostal":"06600","municipioAlcaldia":"Cuauhtémoc","estado":"Ciudad de México","pais":"México"}}
            """.formatted(n, n, n, n);
        String response = mockMvc.perform(post("/api/v1/accounts/{id}/companies", accountId)
                .header("Authorization", bearer).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("publicCompanyId").asText();
    }

    String createBranch(String companyId, String bearer, String type) throws Exception {
        int n = next();
        String body = """
            {"code":"SUC-%d","name":"Sucursal %d","type":"%s","phoneNumber":"5555555555",
            "email":"sucursal-%d@example.com","rfc":"SUC%010d","fiscalName":"Sucursal Fiscal %d",
            "fiscalPostalCode":"06000","centroCostoId":"cc_%d","almacenId":"alm_%d","listaPreciosId":"lp_retail",
            "serieFacturacion":"SERIE","folioActual":1,"moneda":"MXN","responsableId":"usr_resp","fechaApertura":"2026-01-01",
            "domicilio":{"calle":"Juárez","numeroExterior":"50","numeroInterior":"","colonia":"Centro",
            "codigoPostal":"06000","municipioAlcaldia":"Cuauhtémoc","estado":"Ciudad de México","pais":"México"}}
            """.formatted(n, n, type, n, n, n, n, n);
        String response = mockMvc.perform(post("/api/v1/companies/{id}/branches", companyId)
                .header("Authorization", bearer).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("publicBranchId").asText();
    }

    void activateCompany(String companyId, String bearer) throws Exception {
        mockMvc.perform(post("/api/v1/companies/{id}/activation", companyId)
                .header("Authorization", bearer).header("Idempotency-Key", UUID.randomUUID().toString()))
            .andExpect(status().isOk());
    }

    void activateBranch(String companyId, String branchId, String bearer) throws Exception {
        mockMvc.perform(post("/api/v1/companies/{id}/branches/{branchId}/activation", companyId, branchId)
                .header("Authorization", bearer).header("Idempotency-Key", UUID.randomUUID().toString()))
            .andExpect(status().isOk());
    }

    void addMember(String companyId, String userId, String status, String rolesJson) {
        jdbc.update("""
            INSERT INTO company_management.company_membership
              (id, public_membership_id, public_company_id, user_id, status, roles_json, created_at, updated_at, version)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """, UUID.randomUUID(), "mem_" + UUID.randomUUID(), companyId, userId, status, rolesJson);
    }

    /** Asigna una sucursal a la membresía de la persona en esa empresa. */
    void assignBranch(String companyId, String userId, String branchId) {
        String membershipId = jdbc.queryForObject(
            "SELECT public_membership_id FROM company_management.company_membership WHERE public_company_id=? AND user_id=?",
            String.class, companyId, userId);
        jdbc.update("""
            INSERT INTO company_management.branch_assignment
              (id, public_assignment_id, public_membership_id, public_branch_id, status, created_at, updated_at, version)
            VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """, UUID.randomUUID(), "asg_" + UUID.randomUUID(), membershipId, branchId);
    }

    /** Los claims que el token declara, tal como los leen el resto de módulos. */
    JsonNode claimsOf(String bearer) throws Exception {
        String payload = bearer.substring("Bearer ".length()).split("\\.")[1];
        return objectMapper.readTree(new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8));
    }
}
