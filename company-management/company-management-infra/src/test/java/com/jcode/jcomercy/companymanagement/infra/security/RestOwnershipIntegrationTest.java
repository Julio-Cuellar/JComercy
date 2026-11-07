package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ERR-008: cada ruta REST de cuentas, empresas y sucursales solo atiende a quien es de esa cuenta o tiene acceso a
 * esa empresa. Quien trae un token válido de otra cuenta recibe 403 y no se modifica nada.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RestOwnershipIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger(500_000);
    private static final String ACCOUNT_DENIED = "No tienes acceso a esta cuenta. Inicia sesión con la cuenta que corresponde.";
    private static final String COMPANY_DENIED = "No tienes acceso a esta empresa. Pide acceso a quien administra la cuenta.";
    private static final String COMPANY_NOT_CHOSEN =
        "Elige primero la empresa con la que vas a trabajar. Entra a una desde «Tus empresas» e intenta de nuevo.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private String accountA;
    private String accountB;
    private String emailOfB;
    private String tokenA;
    private String tokenB;
    private String tokenAInCompany;
    private String tokenBNamingCompanyOfA;
    private String companyOfA;
    private String branchOfA;

    @BeforeEach
    void world() throws Exception {
        Registered ownerA = register();
        Registered ownerB = register();
        accountA = ownerA.accountId();
        accountB = ownerB.accountId();
        emailOfB = ownerB.email();
        tokenA = bearer(accountA, List.of());
        tokenB = bearer(accountB, List.of());
        // El token de la cuenta se emitió antes de crear la empresa: el dueño entra a ella sin renovarlo.
        companyOfA = createCompany(accountA, tokenA);
        tokenAInCompany = enter(accountA, companyOfA, tokenA);
        branchOfA = createBranch(companyOfA, tokenAInCompany);
        // Un token que nombra la empresa de A pero es de B no se puede obtener por la API. Se fabrica para comprobar
        // que, aun así, la pertenencia la decide la base de datos y no lo que el token dice.
        tokenBNamingCompanyOfA = companyBearer(accountB, companyOfA);
    }

    // ---- Rutas de cuenta ----

    @Test
    @DisplayName("GET /accounts/{id}: otra cuenta recibe 403")
    void readingAnotherAccountIsForbidden() throws Exception {
        forbidden(mockMvc.perform(get("/api/v1/accounts/{id}", accountA).header("Authorization", tokenB)), ACCOUNT_DENIED);
    }

    @Test
    @DisplayName("POST /accounts/{id}/suspension: otra cuenta recibe 403 y la cuenta no se suspende")
    void suspendingAnotherAccountIsForbiddenAndChangesNothing() throws Exception {
        forbidden(mockMvc.perform(post("/api/v1/accounts/{id}/suspension", accountA)
            .header("Authorization", tokenB).header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Prueba\"}")), ACCOUNT_DENIED);

        assertThat(statusOf("/api/v1/accounts/" + accountA, tokenA)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("GET /accounts/{id}/companies: otra cuenta recibe 403")
    void listingCompaniesOfAnotherAccountIsForbidden() throws Exception {
        forbidden(mockMvc.perform(get("/api/v1/accounts/{id}/companies", accountA).header("Authorization", tokenB)), ACCOUNT_DENIED);
    }

    @Test
    @DisplayName("POST /accounts/{id}/companies: otra cuenta recibe 403 y no se crea la empresa")
    void creatingACompanyInAnotherAccountIsForbiddenAndChangesNothing() throws Exception {
        forbidden(mockMvc.perform(post("/api/v1/accounts/{id}/companies", accountA)
            .header("Authorization", tokenB).header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON).content(companyJson(SEQ.incrementAndGet()))), ACCOUNT_DENIED);

        mockMvc.perform(get("/api/v1/accounts/{id}/companies", accountA).header("Authorization", tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));
    }

    // ---- Rutas de empresa ----

    @Test
    @DisplayName("GET /companies/{id}: quien no tiene acceso a la empresa recibe 403")
    void readingAnotherCompanyIsForbidden() throws Exception {
        forbidden(mockMvc.perform(get("/api/v1/companies/{id}", companyOfA).header("Authorization", tokenBNamingCompanyOfA)), COMPANY_DENIED);
    }

    @Test
    @DisplayName("POST /companies/{id}/activation: quien no tiene acceso recibe 403 y la empresa no se activa")
    void activatingAnotherCompanyIsForbiddenAndChangesNothing() throws Exception {
        String before = statusOf("/api/v1/companies/" + companyOfA, tokenAInCompany);

        forbidden(mockMvc.perform(post("/api/v1/companies/{id}/activation", companyOfA)
            .header("Authorization", tokenBNamingCompanyOfA).header("Idempotency-Key", UUID.randomUUID().toString())), COMPANY_DENIED);

        assertThat(statusOf("/api/v1/companies/" + companyOfA, tokenAInCompany)).isEqualTo(before);
    }

    // ---- Rutas de sucursal ----

    @Test
    @DisplayName("GET /companies/{id}/branches: quien no tiene acceso a la empresa recibe 403")
    void listingBranchesOfAnotherCompanyIsForbidden() throws Exception {
        forbidden(mockMvc.perform(get("/api/v1/companies/{id}/branches", companyOfA).header("Authorization", tokenBNamingCompanyOfA)), COMPANY_DENIED);
    }

    @Test
    @DisplayName("POST /companies/{id}/branches: quien no tiene acceso recibe 403 y no se crea la sucursal")
    void creatingABranchInAnotherCompanyIsForbiddenAndChangesNothing() throws Exception {
        forbidden(mockMvc.perform(post("/api/v1/companies/{id}/branches", companyOfA)
            .header("Authorization", tokenBNamingCompanyOfA).header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON).content(branchJson(SEQ.incrementAndGet(), "SUCURSAL"))), COMPANY_DENIED);

        mockMvc.perform(get("/api/v1/companies/{id}/branches", companyOfA).header("Authorization", tokenAInCompany))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("GET /companies/{id}/branches/{branchId}: quien no tiene acceso recibe 403")
    void readingABranchOfAnotherCompanyIsForbidden() throws Exception {
        forbidden(mockMvc.perform(get("/api/v1/companies/{id}/branches/{branchId}", companyOfA, branchOfA)
            .header("Authorization", tokenBNamingCompanyOfA)), COMPANY_DENIED);
    }

    @Test
    @DisplayName("POST /companies/{id}/branches/{branchId}/activation: quien no tiene acceso recibe 403 y la sucursal no cambia")
    void activatingABranchOfAnotherCompanyIsForbiddenAndChangesNothing() throws Exception {
        String before = statusOf("/api/v1/companies/" + companyOfA + "/branches/" + branchOfA, tokenAInCompany);

        forbidden(mockMvc.perform(post("/api/v1/companies/{id}/branches/{branchId}/activation", companyOfA, branchOfA)
            .header("Authorization", tokenBNamingCompanyOfA).header("Idempotency-Key", UUID.randomUUID().toString())), COMPANY_DENIED);

        assertThat(statusOf("/api/v1/companies/" + companyOfA + "/branches/" + branchOfA, tokenAInCompany)).isEqualTo(before);
    }

    // ---- Quien sí tiene acceso sigue operando ----

    @Test
    @DisplayName("El dueño lee su cuenta y sus empresas")
    void ownerReadsItsAccountAndCompanies() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", accountA).header("Authorization", tokenA))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/accounts/{id}/companies", accountA).header("Authorization", tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("El dueño opera la empresa recién creada: entra a ella con el token de su cuenta, sin renovarlo")
    void ownerOperatesTheCompanyJustCreatedWithoutRenewingTheToken() throws Exception {
        mockMvc.perform(get("/api/v1/companies/{id}", companyOfA).header("Authorization", tokenAInCompany))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/companies/{id}/activation", companyOfA)
                .header("Authorization", tokenAInCompany).header("Idempotency-Key", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(get("/api/v1/companies/{id}/branches/{branchId}", companyOfA, branchOfA).header("Authorization", tokenAInCompany))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un miembro con membresía activa y un rol que lo permite lee la empresa, pero no entra a la cuenta del dueño")
    void memberWithAnActiveMembershipReadsTheCompanyButNotTheAccount() throws Exception {
        String memberId = "usr_member_" + SEQ.incrementAndGet();
        // La pertenencia sale de la base de datos y no del token: el token no nombra ninguna empresa (ERR-011).
        jdbc.update("""
            INSERT INTO company_management.company_membership
              (id, public_membership_id, public_company_id, user_id, status, roles_json, created_at, updated_at, version)
            VALUES (?, ?, ?, ?, 'ACTIVE', '["COMPANY_ADMIN"]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """, UUID.randomUUID(), "mem_" + UUID.randomUUID(), companyOfA, memberId);
        String member = enter(memberId, companyOfA, bearer(memberId, List.of()));

        mockMvc.perform(get("/api/v1/companies/{id}", companyOfA).header("Authorization", member))
            .andExpect(status().isOk());
        forbidden(mockMvc.perform(get("/api/v1/accounts/{id}", accountA).header("Authorization", member)), ACCOUNT_DENIED);
    }

    @Test
    @DisplayName("El token que entrega el inicio de sesión sirve para la propia cuenta, no para la de otro ni para trabajar en una empresa")
    void tokenFromLoginWorksForTheOwnAccountOnly() throws Exception {
        String login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"ClaveSegura123!\"}".formatted(emailOfB)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String token = "Bearer " + objectMapper.readTree(login).get("accessToken").asText();

        mockMvc.perform(get("/api/v1/accounts/{id}", accountB).header("Authorization", token))
            .andExpect(status().isOk());
        forbidden(mockMvc.perform(get("/api/v1/accounts/{id}", accountA).header("Authorization", token)), ACCOUNT_DENIED);
        forbidden(mockMvc.perform(get("/api/v1/companies/{id}", companyOfA).header("Authorization", token)), COMPANY_NOT_CHOSEN);
    }

    @Test
    @DisplayName("Una empresa que no existe responde 403 a quien no es miembro, sin revelar si existe")
    void aNonExistentCompanyRespondsForbiddenToANonMember() throws Exception {
        String missing = "com_no_existe_" + SEQ.incrementAndGet();
        forbidden(mockMvc.perform(get("/api/v1/companies/{id}", missing)
            .header("Authorization", companyBearer(accountB, missing))), COMPANY_DENIED);
    }

    @Test
    @DisplayName("Toda ruta REST es pública o cuelga de una cuenta o empresa con las variables que el interceptor comprueba")
    void everyRestRouteIsPublicOrCoveredByTheOwnershipCheck() throws Exception {
        String generated = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        Set<String> uncovered = new TreeSet<>();
        objectMapper.readTree(generated).get("paths").fieldNames().forEachRemaining(path -> {
            // Públicas a propósito: quien las usa todavía no tiene acceso. La invitación se identifica con el código del
            // correo (de 32 bytes, de un solo uso y con vencimiento), y un código que no sirve responde siempre lo mismo.
            boolean isPublic = path.equals("/api/v1/accounts/registration") || path.startsWith("/api/v1/auth/")
                || path.startsWith("/api/v1/employee-invitations/");
            boolean underAnAccount = path.startsWith("/api/v1/accounts/{publicAccountId}");
            boolean underACompany = path.startsWith("/api/v1/companies/{publicCompanyId}");
            if (!isPublic && !underAnAccount && !underACompany) {
                uncovered.add(path);
            }
        });

        assertThat(uncovered).as("rutas REST sin comprobación de pertenencia").isEmpty();
    }

    // ---- Ayudas ----

    private void forbidden(ResultActions result, String detail) throws Exception {
        result.andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_PERMISSIONS"))
            .andExpect(jsonPath("$.detail").value(detail));
    }

    private String statusOf(String url, String authorization) throws Exception {
        String body = mockMvc.perform(get(url).header("Authorization", authorization))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("status").asText();
    }

    private String bearer(String subject, List<String> companyIds) {
        return "Bearer " + tokenService.issue(subject, List.of(), companyIds, List.of());
    }

    /** Un token que nombra esa empresa, como el que se entrega al entrar a ella; aquí se fabrica sin pasar por la ruta. */
    private String companyBearer(String subject, String companyId) {
        return "Bearer " + tokenService.issueCompanyToken(subject, companyId, List.of(), List.of());
    }

    /** Entra a la empresa con el token de la cuenta y devuelve el token de esa empresa. */
    private String enter(String accountId, String companyId, String accountBearer) throws Exception {
        String response = mockMvc.perform(post("/api/v1/accounts/{id}/company-accesses/{target}/session", accountId, companyId)
                .header("Authorization", accountBearer))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("accessToken").asText();
    }

    private record Registered(String accountId, String email) { }

    private Registered register() throws Exception {
        int n = SEQ.incrementAndGet();
        String email = "dueno-%d@example.com".formatted(n);
        String body = """
            {"name":"Cuenta %d","owner":{"firstName":"Dueño","lastName":"Prueba","birthDate":"1985-04-12",
            "rfc":"TST%010d","phoneNumber":"5512345678","email":"%s",
            "password":"ClaveSegura123!","state":"Ciudad de México","address":"Av. Reforma 100"}}
            """.formatted(n, n, email);
        String response = mockMvc.perform(post("/api/v1/accounts/registration")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return new Registered(objectMapper.readTree(response).get("publicAccountId").asText(), email);
    }

    private String createCompany(String accountId, String authorization) throws Exception {
        String response = mockMvc.perform(post("/api/v1/accounts/{id}/companies", accountId)
                .header("Authorization", authorization).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(companyJson(SEQ.incrementAndGet())))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("publicCompanyId").asText();
    }

    private String createBranch(String companyId, String authorization) throws Exception {
        String response = mockMvc.perform(post("/api/v1/companies/{id}/branches", companyId)
                .header("Authorization", authorization).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(branchJson(SEQ.incrementAndGet(), "MATRIZ")))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("publicBranchId").asText();
    }

    private static String companyJson(int n) {
        return """
            {"legalName":"Empresa %d S.A.","tradeName":"Empresa %d","rfc":"RFC%010d","taxRegime":"601",
            "incorporationDate":"2020-01-15","phone":"5512345678","email":"empresa-%d@example.com",
            "website":"https://example.com","logoUrl":"https://example.com/logo.png",
            "fiscalAddress":{"calle":"Reforma","numeroExterior":"100","numeroInterior":"10","colonia":"Juárez",
            "codigoPostal":"06600","municipioAlcaldia":"Cuauhtémoc","estado":"Ciudad de México","pais":"México"}}
            """.formatted(n, n, n, n);
    }

    private static String branchJson(int n, String type) {
        return """
            {"code":"SUC-%d","name":"Sucursal %d","type":"%s","phoneNumber":"5555555555",
            "email":"sucursal-%d@example.com","rfc":"SUC%010d","fiscalName":"Sucursal Fiscal %d",
            "fiscalPostalCode":"06000","centroCostoId":"cc_%d","almacenId":"alm_%d","listaPreciosId":"lp_retail",
            "serieFacturacion":"SERIE","folioActual":1,"moneda":"MXN","responsableId":"usr_resp","fechaApertura":"2026-01-01",
            "domicilio":{"calle":"Juárez","numeroExterior":"50","numeroInterior":"","colonia":"Centro",
            "codigoPostal":"06000","municipioAlcaldia":"Cuauhtémoc","estado":"Ciudad de México","pais":"México"}}
            """.formatted(n, n, type, n, n, n, n, n);
    }
}
