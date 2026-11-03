package com.jcode.jcomercy.companymanagement.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ERR-011, fase B3: cada ruta exige el token que le corresponde. Las de una empresa, el token de esa empresa; las de la
 * cuenta, el de la cuenta. Así un token de empresa robado no administra la cuenta ni lista las demás empresas, y el
 * token de la cuenta no trabaja dentro de una empresa sin haber entrado a ella.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TokenKindEnforcementIntegrationTest {

    private static final String CHOOSE_COMPANY =
        "Elige primero la empresa con la que vas a trabajar. Entra a una desde «Tus empresas» e intenta de nuevo.";
    private static final String OTHER_COMPANY =
        "Tu sesión es de otra empresa. Entra a esta empresa desde «Tus empresas» e intenta de nuevo.";
    private static final String ACCOUNT_ONLY =
        "Esto se hace desde tu cuenta, no desde una empresa. Vuelve a tu cuenta e intenta de nuevo.";
    private static final String ACCOUNT_DENIED = "No tienes acceso a esta cuenta. Inicia sesión con la cuenta que corresponde.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${security.jwt.issuer}")
    private String jwtIssuer;

    private RestWorld world;
    private RestWorld.Person alice;
    private RestWorld.Person bob;
    private String aliceAccount;
    private String bobAccount;
    private String companyA1;
    private String companyA2;
    private String companyB;
    private String aliceInA1;
    private String aliceInA2;

    /** Alice es dueña de dos empresas, A1 y A2; Bob es dueño de la empresa B. */
    @BeforeEach
    void people() throws Exception {
        world = new RestWorld(mockMvc, objectMapper, jdbc);
        alice = world.register();
        bob = world.register();
        aliceAccount = world.login(alice.email());
        bobAccount = world.login(bob.email());
        companyA1 = world.createCompany(alice.accountId(), aliceAccount);
        companyA2 = world.createCompany(alice.accountId(), aliceAccount);
        companyB = world.createCompany(bob.accountId(), bobAccount);
        aliceInA1 = world.enter(alice.accountId(), companyA1, aliceAccount);
        aliceInA2 = world.enter(alice.accountId(), companyA2, aliceAccount);
    }

    // ---- Las rutas de una empresa exigen el token de esa empresa ----

    @Test
    @DisplayName("Una ruta de empresa no atiende al token de la cuenta: primero hay que entrar a la empresa")
    void aCompanyRouteRejectsTheAccountToken() throws Exception {
        String before = statusOf("/api/v1/companies/" + companyA1, aliceInA1);

        denied(mockMvc.perform(get("/api/v1/companies/{id}", companyA1).header("Authorization", aliceAccount)), CHOOSE_COMPANY);
        denied(mockMvc.perform(get("/api/v1/companies/{id}/branches", companyA1).header("Authorization", aliceAccount)), CHOOSE_COMPANY);
        denied(mockMvc.perform(post("/api/v1/companies/{id}/activation", companyA1)
            .header("Authorization", aliceAccount).header("Idempotency-Key", UUID.randomUUID().toString())), CHOOSE_COMPANY);

        assertThat(statusOf("/api/v1/companies/" + companyA1, aliceInA1)).isEqualTo(before);
        mockMvc.perform(get("/api/v1/companies/{id}", companyA1).header("Authorization", aliceInA1))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("El token de una empresa no sirve en otra, ni en una ajena, ni en una que no existe, y la respuesta es la misma")
    void aCompanyTokenDoesNotWorkInAnotherCompany() throws Exception {
        for (String other : List.of(companyA2, companyB, "com_missing")) {
            denied(mockMvc.perform(get("/api/v1/companies/{id}", other).header("Authorization", aliceInA1)), OTHER_COMPANY);
            denied(mockMvc.perform(get("/api/v1/companies/{id}/branches", other).header("Authorization", aliceInA1)), OTHER_COMPANY);
        }
        mockMvc.perform(get("/api/v1/companies/{id}", companyA2).header("Authorization", aliceInA2))
            .andExpect(status().isOk());
    }

    // ---- Las rutas de la cuenta exigen el token de la cuenta ----

    @Test
    @DisplayName("Una ruta de la cuenta no atiende al token de una empresa, aunque la empresa sea suya")
    void anAccountRouteRejectsACompanyToken() throws Exception {
        String account = alice.accountId();
        String before = statusOf("/api/v1/accounts/" + account, aliceAccount);

        denied(mockMvc.perform(get("/api/v1/accounts/{id}", account).header("Authorization", aliceInA1)), ACCOUNT_ONLY);
        denied(mockMvc.perform(get("/api/v1/accounts/{id}/companies", account).header("Authorization", aliceInA1)), ACCOUNT_ONLY);
        denied(mockMvc.perform(get("/api/v1/accounts/{id}/company-accesses", account).header("Authorization", aliceInA1)), ACCOUNT_ONLY);
        // Desde una empresa no se salta a otra: hay que volver a la cuenta.
        denied(mockMvc.perform(post("/api/v1/accounts/{id}/company-accesses/{target}/session", account, companyA2)
            .header("Authorization", aliceInA1)), ACCOUNT_ONLY);
        denied(mockMvc.perform(post("/api/v1/accounts/{id}/suspension", account)
            .header("Authorization", aliceInA1).header("Idempotency-Key", UUID.randomUUID().toString())), ACCOUNT_ONLY);

        assertThat(statusOf("/api/v1/accounts/" + account, aliceAccount)).isEqualTo(before);
    }

    @Test
    @DisplayName("Con el token de la cuenta, las rutas de la cuenta siguen funcionando")
    void theAccountTokenStillWorksOnAccountRoutes() throws Exception {
        String account = alice.accountId();

        mockMvc.perform(get("/api/v1/accounts/{id}", account).header("Authorization", aliceAccount))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/accounts/{id}/companies", account).header("Authorization", aliceAccount))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get("/api/v1/accounts/{id}/company-accesses", account).header("Authorization", aliceAccount))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("El token de empresa de otra persona se rechaza por pertenencia, antes que por tipo")
    void anotherPersonsCompanyTokenFailsOwnershipFirst() throws Exception {
        String bobInB = world.enter(bob.accountId(), companyB, bobAccount);

        denied(mockMvc.perform(get("/api/v1/accounts/{id}", alice.accountId()).header("Authorization", bobInB)), ACCOUNT_DENIED);
    }

    @Test
    @DisplayName("Un token cuyo contexto de empresa no es un texto válido no se acepta, ni siquiera como token de cuenta")
    void aTokenWithAMalformedCompanyContextIsNotAccepted() throws Exception {
        for (Object malformed : List.of(42, "", "   ")) {
            String forged = "Bearer " + Jwts.builder().subject(alice.accountId()).issuer(jwtIssuer)
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 300_000))
                .claim("ctx", malformed)
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8))).compact();

            mockMvc.perform(get("/api/v1/accounts/{id}", alice.accountId()).header("Authorization", forged))
                .andExpect(status().isUnauthorized());
        }
    }

    // ---- GraphQL exige lo mismo que REST ----

    @Test
    @DisplayName("GraphQL: la lista de empresas de la cuenta solo se entrega al token de la cuenta")
    void graphQlListsTheCompaniesOnlyToTheAccountToken() throws Exception {
        graphql(aliceInA1, "CompaniesByAccount", Map.of("publicAccountId", alice.accountId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.companiesByAccount.length()").value(0));
        graphql(aliceAccount, "CompaniesByAccount", Map.of("publicAccountId", alice.accountId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.companiesByAccount.length()").value(2));
    }

    @Test
    @DisplayName("GraphQL: una empresa solo se entrega al token de esa empresa")
    void graphQlReturnsACompanyOnlyToTheTokenOfThatCompany() throws Exception {
        graphql(aliceAccount, "Company", Map.of("publicCompanyId", companyA1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.company").doesNotExist());
        graphql(aliceInA2, "Company", Map.of("publicCompanyId", companyA1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.company").doesNotExist());
        graphql(aliceInA1, "Company", Map.of("publicCompanyId", companyA1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.company.publicCompanyId").value(companyA1));
    }

    // ---- Ayudas ----

    private void denied(ResultActions result, String detail) throws Exception {
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

    /** Ejecuta una de las operaciones registradas en el proyecto (graphql/operations/{name}.graphql). */
    private ResultActions graphql(String authorization, String operation, Map<String, Object> variables) throws Exception {
        String document = new String(new ClassPathResource("graphql/operations/" + operation + ".graphql")
            .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        ResultActions result = mockMvc.perform(post("/graphql/v1").contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", authorization)
            .content(objectMapper.writeValueAsString(Map.of("query", document, "variables", variables))));
        return result.andReturn().getRequest().isAsyncStarted() ? mockMvc.perform(asyncDispatch(result.andReturn())) : result;
    }
}
