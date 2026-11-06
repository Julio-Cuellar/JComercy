package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ERR-011: la persona ve sus empresas y entra a una. Entrar entrega un token que nombra solo esa empresa, con los
 * permisos que la persona tiene en ella y no los de ninguna otra.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompanyAccessIntegrationTest {

    private static final String COMPANY_DENIED = "No tienes acceso a esta empresa. Pide acceso a quien administra la cuenta.";
    private static final String ACCOUNT_DENIED = "No tienes acceso a esta cuenta. Inicia sesión con la cuenta que corresponde.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private RestWorld world;
    private RestWorld.Person alice;
    private RestWorld.Person bob;
    private RestWorld.Person carlos;
    private String aliceLogin;
    private String bobLogin;
    private String companyA;
    private String branchA;
    private String companyB;
    private String branchB1;
    private String branchB2;
    private String companyC;

    /**
     * Alice es dueña de la empresa A y personal de la empresa B, que es de Bob. Carlos es dueño de la empresa C, a la
     * que Alice no pertenece.
     */
    @BeforeEach
    void people() throws Exception {
        world = new RestWorld(mockMvc, objectMapper, jdbc);
        alice = world.register();
        bob = world.register();
        carlos = world.register();
        aliceLogin = world.login(alice.email());
        bobLogin = world.login(bob.email());
        String carlosLogin = world.login(carlos.email());

        // Dentro de una empresa se trabaja con el token de esa empresa: cada dueña entra primero a la suya.
        companyA = world.createCompany(alice.accountId(), aliceLogin);
        String aliceInA = world.enter(alice.accountId(), companyA, aliceLogin);
        branchA = world.createBranch(companyA, aliceInA, "MATRIZ");
        world.activateCompany(companyA, aliceInA);
        world.activateBranch(companyA, branchA, aliceInA);

        companyB = world.createCompany(bob.accountId(), bobLogin);
        String bobInB = world.enter(bob.accountId(), companyB, bobLogin);
        branchB1 = world.createBranch(companyB, bobInB, "MATRIZ");
        world.activateCompany(companyB, bobInB);
        world.activateBranch(companyB, branchB1, bobInB);
        // La segunda sucursal también está activa: así, que Alice no la reciba se debe a que no la tiene asignada.
        branchB2 = world.createBranch(companyB, bobInB, "SUCURSAL");
        world.activateBranch(companyB, branchB2, bobInB);
        world.addMember(companyB, alice.accountId(), "ACTIVE", "[\"STAFF\"]");

        companyC = world.createCompany(carlos.accountId(), carlosLogin);
    }

    // ---- Tus empresas ----

    @Test
    @DisplayName("Lista las empresas que la cuenta posee y aquellas donde es miembro")
    void listsTheOwnedCompaniesAndTheOnesWhereItIsAMember() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/company-accesses", alice.accountId()).header("Authorization", aliceLogin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[?(@.publicCompanyId=='%s')].relationship".formatted(companyA)).value("OWNER"))
            .andExpect(jsonPath("$[?(@.publicCompanyId=='%s')].relationship".formatted(companyB)).value("MEMBER"))
            .andExpect(jsonPath("$[?(@.publicCompanyId=='%s')].roles[0]".formatted(companyB)).value("STAFF"))
            .andExpect(jsonPath("$[?(@.publicCompanyId=='%s')].tradeName".formatted(companyA)).isNotEmpty())
            .andExpect(jsonPath("$[?(@.publicCompanyId=='%s')].status".formatted(companyA)).value("ACTIVE"));
    }

    @Test
    @DisplayName("No lista empresas ajenas ni empresas donde la membresía está suspendida")
    void doesNotListForeignCompaniesNorSuspendedMemberships() throws Exception {
        world.addMember(companyC, alice.accountId(), "SUSPENDED", "[\"STAFF\"]");

        mockMvc.perform(get("/api/v1/accounts/{id}/company-accesses", alice.accountId()).header("Authorization", aliceLogin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[?(@.publicCompanyId=='%s')]".formatted(companyC)).isEmpty());
    }

    @Test
    @DisplayName("Otra cuenta no puede ver las empresas de esta")
    void anotherAccountCannotListMyCompanies() throws Exception {
        denied(mockMvc.perform(get("/api/v1/accounts/{id}/company-accesses", alice.accountId()).header("Authorization", bobLogin)),
            ACCOUNT_DENIED);
    }

    // ---- Entrar a una empresa ----

    @Test
    @DisplayName("Entrar a una empresa propia entrega un token que nombra solo esa empresa y lo puede todo en ella")
    void enteringAnOwnedCompanyReturnsACompanyTokenWithEverythingInIt() throws Exception {
        JsonNode response = enter(aliceLogin, alice.accountId(), companyA);

        assertThat(response.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(response.get("expiresIn").asLong()).isEqualTo(3600);
        assertThat(response.get("publicCompanyId").asText()).isEqualTo(companyA);

        JsonNode claims = world.claimsOf("Bearer " + response.get("accessToken").asText());
        assertThat(claims.get("sub").asText()).isEqualTo(alice.accountId());
        assertThat(claims.get("ctx").asText()).isEqualTo(companyA);
        assertThat(texts(claims.get("companyIds"))).containsExactly(companyA);
        assertThat(texts(claims.get("branchIds"))).containsExactly(branchA);
        assertThat(claims.get("permissions").asText()).isEqualTo("*");
    }

    @Test
    @DisplayName("ERR-011: entrar como miembro entrega solo los permisos de ese rol y nunca el * ni la otra empresa")
    void enteringAsAMemberNeverMixesTheOtherCompany() throws Exception {
        // Alice es dueña de A y personal de B. Con el token de hoy llevaría el * de A también en B.
        JsonNode claims = world.claimsOf("Bearer " + enter(aliceLogin, alice.accountId(), companyB).get("accessToken").asText());

        assertThat(claims.get("ctx").asText()).isEqualTo(companyB);
        assertThat(texts(claims.get("companyIds"))).containsExactly(companyB);
        assertThat(List.of(claims.get("permissions").asText().split(" ")))
            .containsExactlyInAnyOrder("operational_context.select", "inventory.stock.read", "effective_permissions.read_own");
    }

    @Test
    @DisplayName("El token de un miembro lleva solo las sucursales que tiene asignadas en esa empresa")
    void aMemberTokenCarriesOnlyTheAssignedBranchesOfThatCompany() throws Exception {
        world.assignBranch(companyB, alice.accountId(), branchB1);

        JsonNode claims = world.claimsOf("Bearer " + enter(aliceLogin, alice.accountId(), companyB).get("accessToken").asText());

        assertThat(texts(claims.get("branchIds"))).containsExactly(branchB1).doesNotContain(branchB2, branchA);
    }

    @Test
    @DisplayName("Entrar a una empresa sin acceso responde igual que entrar a una que no existe")
    void enteringACompanyWithoutAccessLooksLikeANonExistentOne() throws Exception {
        denied(post("/api/v1/accounts/{id}/company-accesses/{target}/session", alice.accountId(), companyC), aliceLogin, COMPANY_DENIED);
        denied(post("/api/v1/accounts/{id}/company-accesses/{target}/session", alice.accountId(), "com_no_existe_1"), aliceLogin, COMPANY_DENIED);
    }

    @Test
    @DisplayName("Una membresía suspendida no deja entrar")
    void aSuspendedMembershipCannotEnter() throws Exception {
        world.addMember(companyC, alice.accountId(), "SUSPENDED", "[\"STAFF\"]");

        denied(post("/api/v1/accounts/{id}/company-accesses/{target}/session", alice.accountId(), companyC), aliceLogin, COMPANY_DENIED);
    }

    @Test
    @DisplayName("Otra cuenta no puede entrar a una empresa usando esta cuenta")
    void anotherAccountCannotEnterThroughMyAccount() throws Exception {
        denied(post("/api/v1/accounts/{id}/company-accesses/{target}/session", alice.accountId(), companyA), bobLogin, ACCOUNT_DENIED);
    }

    // ---- Ayudas ----

    private JsonNode enter(String bearer, String accountId, String companyId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/accounts/{id}/company-accesses/{target}/session", accountId, companyId)
                .header("Authorization", bearer))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void denied(MockHttpServletRequestBuilder request, String bearer, String detail) throws Exception {
        denied(mockMvc.perform(request.header("Authorization", bearer)), detail);
    }

    private void denied(ResultActions result, String detail) throws Exception {
        result.andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_PERMISSIONS"))
            .andExpect(jsonPath("$.detail").value(detail));
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }
}
