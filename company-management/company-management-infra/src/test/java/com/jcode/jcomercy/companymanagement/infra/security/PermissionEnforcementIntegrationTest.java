package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ERR-010: dentro de su empresa, cada persona hace solo lo que su rol permite. El dueño de la cuenta lo puede todo;
 * quien entra por una membresía recibe únicamente los permisos de sus roles en esa empresa.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PermissionEnforcementIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger(700_000);
    private static final String PASSWORD = "ClaveSegura123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private Registered owner;
    private String tokenOfOwner;
    private String companyId;
    private String branchId;

    @BeforeEach
    void world() throws Exception {
        owner = register();
        tokenOfOwner = login(owner.email());
        companyId = createCompany(owner.accountId(), tokenOfOwner);
        branchId = createBranch(companyId, tokenOfOwner);
    }

    // ---- Lo que cada rol puede y no puede hacer ----

    @Test
    @DisplayName("Quien solo tiene el rol de personal no puede consultar la empresa")
    void staffCannotReadTheCompany() throws Exception {
        String staff = memberToken("[\"STAFF\"]");

        denied(mockMvc.perform(get("/api/v1/companies/{id}", companyId).header("Authorization", staff)),
            "Tu rol no permite consultar la empresa. Pide acceso a quien administra la cuenta.");
    }

    @Test
    @DisplayName("Un encargado de sucursal consulta sucursales, pero no las crea ni las activa")
    void branchManagerReadsBranchesButCannotCreateOrActivateThem() throws Exception {
        String manager = memberToken("[\"BRANCH_MANAGER\"]");

        mockMvc.perform(get("/api/v1/companies/{id}/branches", companyId).header("Authorization", manager))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/companies/{id}/branches/{branchId}", companyId, branchId).header("Authorization", manager))
            .andExpect(status().isOk());

        String before = statusOf("/api/v1/companies/" + companyId + "/branches/" + branchId, tokenOfOwner);
        denied(mockMvc.perform(post("/api/v1/companies/{id}/branches", companyId)
                .header("Authorization", manager).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(branchJson(SEQ.incrementAndGet(), "SUCURSAL"))),
            "Tu rol no permite crear sucursales. Pide acceso a quien administra la cuenta.");
        denied(mockMvc.perform(post("/api/v1/companies/{id}/branches/{branchId}/activation", companyId, branchId)
                .header("Authorization", manager).header("Idempotency-Key", UUID.randomUUID().toString())),
            "Tu rol no permite activar sucursales. Pide acceso a quien administra la cuenta.");

        mockMvc.perform(get("/api/v1/companies/{id}/branches", companyId).header("Authorization", tokenOfOwner))
            .andExpect(jsonPath("$", hasSize(1)));
        assertThat(statusOf("/api/v1/companies/" + companyId + "/branches/" + branchId, tokenOfOwner)).isEqualTo(before);
    }

    @Test
    @DisplayName("Un administrador de empresa opera la empresa y sus sucursales, pero no entra a la cuenta del dueño")
    void companyAdminOperatesTheCompanyButNotTheAccount() throws Exception {
        String admin = memberToken("[\"COMPANY_ADMIN\"]");

        mockMvc.perform(get("/api/v1/companies/{id}", companyId).header("Authorization", admin))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/companies/{id}/activation", companyId)
                .header("Authorization", admin).header("Idempotency-Key", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(post("/api/v1/companies/{id}/branches", companyId)
                .header("Authorization", admin).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(branchJson(SEQ.incrementAndGet(), "SUCURSAL")))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/companies/{id}/branches/{branchId}/activation", companyId, branchId)
                .header("Authorization", admin).header("Idempotency-Key", UUID.randomUUID().toString()))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/accounts/{id}", owner.accountId()).header("Authorization", admin))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("No tienes acceso a esta cuenta. Inicia sesión con la cuenta que corresponde."));
    }

    @Test
    @DisplayName("Una membresía suspendida pierde el acceso en la siguiente petición, aunque el token siga vigente")
    void suspendedMembershipLosesAccessAtOnce() throws Exception {
        Registered member = register();
        addMember(member.accountId(), "ACTIVE", "[\"BRANCH_MANAGER\"]");
        String token = login(member.email());

        mockMvc.perform(get("/api/v1/companies/{id}/branches", companyId).header("Authorization", token))
            .andExpect(status().isOk());

        jdbc.update("UPDATE company_management.company_membership SET status='SUSPENDED' WHERE user_id=?", member.accountId());

        denied(mockMvc.perform(get("/api/v1/companies/{id}/branches", companyId).header("Authorization", token)),
            "Tu rol no permite consultar sucursales. Pide acceso a quien administra la cuenta.");
    }

    @Test
    @DisplayName("Un rol propio de la empresa vale en esa empresa, y uno con el mismo nombre de otra empresa no otorga nada")
    void customRoleOnlyAppliesInItsOwnCompany() throws Exception {
        addRole("ENCARGADO_DE_PRUEBA", companyId, "[\"branches.read\"]");
        addRole("ENCARGADO_AJENO", "com_otra_empresa_" + SEQ.incrementAndGet(), "[\"*\"]");

        String reader = memberToken("[\"ENCARGADO_DE_PRUEBA\"]");
        mockMvc.perform(get("/api/v1/companies/{id}/branches", companyId).header("Authorization", reader))
            .andExpect(status().isOk());

        String foreign = memberToken("[\"ENCARGADO_AJENO\"]");
        denied(mockMvc.perform(get("/api/v1/companies/{id}/branches", companyId).header("Authorization", foreign)),
            "Tu rol no permite consultar sucursales. Pide acceso a quien administra la cuenta.");
    }

    // ---- GraphQL exige lo mismo que REST ----

    @Test
    @DisplayName("GraphQL: quien no tiene companies.read no recibe la empresa, aunque su token la nombre")
    void graphQlHidesTheCompanyFromARoleWithoutReadPermission() throws Exception {
        String staff = memberToken("[\"STAFF\"]");

        graphql(staff, "Company", Map.of("publicCompanyId", companyId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.company").doesNotExist());
    }

    @Test
    @DisplayName("GraphQL: un administrador de empresa recibe la empresa")
    void graphQlReturnsTheCompanyToARoleWithReadPermission() throws Exception {
        String admin = memberToken("[\"COMPANY_ADMIN\"]");

        graphql(admin, "Company", Map.of("publicCompanyId", companyId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.company.publicCompanyId").value(companyId));
    }

    @Test
    @DisplayName("GraphQL: el dueño ve la empresa que creó después de iniciar sesión, sin renovar el token")
    void graphQlShowsTheOwnerTheCompanyCreatedAfterLogin() throws Exception {
        graphql(tokenOfOwner, "Company", Map.of("publicCompanyId", companyId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.company.publicCompanyId").value(companyId));
        graphql(tokenOfOwner, "CompaniesByAccount", Map.of("publicAccountId", owner.accountId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.companiesByAccount.length()").value(1));
    }

    // ---- Lo que lleva el token de un inicio de sesión real ----

    @Test
    @DisplayName("El token del dueño de una empresa lleva todos los permisos")
    void ownerTokenCarriesEveryPermission() throws Exception {
        // El token del @BeforeEach se emitió antes de que el dueño tuviera empresa; este ya la tiene.
        assertThat(permissionsIn(login(owner.email()))).contains("*");
    }

    @Test
    @DisplayName("El token de un miembro lleva solo los permisos de su rol, con los de inventario incluidos")
    void memberTokenCarriesOnlyThePermissionsOfItsRole() throws Exception {
        String manager = memberToken("[\"BRANCH_MANAGER\"]");

        assertThat(permissionsIn(manager))
            .contains("branches.read", "inventory.stock.*")
            .doesNotContain("*", "branches.create", "companies.create");
    }

    @Test
    @DisplayName("El token de una cuenta sin empresas ni membresías no lleva permisos")
    void tokenOfAnAccountWithoutCompaniesCarriesNoPermissions() throws Exception {
        assertThat(permissionsIn(login(register().email()))).isEmpty();
    }

    // ---- Ayudas ----

    private void denied(ResultActions result, String detail) throws Exception {
        result.andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_PERMISSIONS"))
            .andExpect(jsonPath("$.detail").value(detail));
    }

    /** Ejecuta una de las operaciones registradas en el proyecto (graphql/operations/{name}.graphql). */
    private ResultActions graphql(String authorization, String operation, Map<String, Object> variables) throws Exception {
        String document = new String(new ClassPathResource("graphql/operations/" + operation + ".graphql")
            .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        ResultActions result = mockMvc.perform(post("/graphql/v1").contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", authorization)
            .content(objectMapper.writeValueAsString(Map.of("query", document, "variables", variables))));
        return result.andReturn().getRequest().isAsyncStarted() ? mockMvc.perform(asyncDispatch(result.andReturn())) : result;
    }

    private String statusOf(String url, String authorization) throws Exception {
        String body = mockMvc.perform(get(url).header("Authorization", authorization))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("status").asText();
    }

    /** Registra otra cuenta, la hace miembro de la empresa del dueño con esos roles y devuelve su token de inicio de sesión. */
    private String memberToken(String rolesJson) throws Exception {
        Registered member = register();
        addMember(member.accountId(), "ACTIVE", rolesJson);
        return login(member.email());
    }

    private void addMember(String userId, String status, String rolesJson) {
        jdbc.update("""
            INSERT INTO company_management.company_membership
              (id, public_membership_id, public_company_id, user_id, status, roles_json, created_at, updated_at, version)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """, UUID.randomUUID(), "mem_" + UUID.randomUUID(), companyId, userId, status, rolesJson);
    }

    private void addRole(String name, String ofCompany, String permissionsJson) {
        jdbc.update("""
            INSERT INTO company_management.role
              (id, public_role_id, public_company_id, name, is_system_role, status, permissions_json, created_at, updated_at, version)
            VALUES (?, ?, ?, ?, FALSE, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """, UUID.randomUUID(), "rol_" + UUID.randomUUID(), ofCompany, name, permissionsJson);
    }

    /** Los permisos que el token declara, tal como los lee el resto de módulos. */
    private Set<String> permissionsIn(String bearer) throws Exception {
        String payload = bearer.substring("Bearer ".length()).split("\\.")[1];
        JsonNode claims = objectMapper.readTree(new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8));
        String permissions = claims.path("permissions").asText("");
        return Arrays.stream(permissions.split(" ")).filter(value -> !value.isBlank()).collect(Collectors.toSet());
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("accessToken").asText();
    }

    private record Registered(String accountId, String email) { }

    private Registered register() throws Exception {
        int n = SEQ.incrementAndGet();
        String email = "permisos-%d@example.com".formatted(n);
        String body = """
            {"name":"Cuenta %d","owner":{"firstName":"Persona","lastName":"Prueba","birthDate":"1985-04-12",
            "rfc":"PRM%010d","phoneNumber":"5512345678","email":"%s",
            "password":"%s","state":"Ciudad de México","address":"Av. Reforma 100"}}
            """.formatted(n, n, email, PASSWORD);
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

    private String createBranch(String company, String authorization) throws Exception {
        String response = mockMvc.perform(post("/api/v1/companies/{id}/branches", company)
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
