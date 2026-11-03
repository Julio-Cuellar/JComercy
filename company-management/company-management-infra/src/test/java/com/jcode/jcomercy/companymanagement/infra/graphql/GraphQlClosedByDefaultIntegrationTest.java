package com.jcode.jcomercy.companymanagement.infra.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.companymanagement.infra.security.JwtTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GraphQL solo ejecuta las operaciones que viven como archivos en el proyecto; todo lo demás se rechaza. */
@SpringBootTest
@AutoConfigureMockMvc
class GraphQlClosedByDefaultIntegrationTest {

    private static final String ACCOUNT = "acc_graphql_100";
    private static final String REJECTION = "Esta consulta no está permitida. Actualiza la aplicación e intenta de nuevo.";
    private static final String UNREGISTERED = "query { companiesByAccount(publicAccountId: \"" + ACCOUNT + "\") { publicCompanyId } }";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService tokenService;

    @Test
    @DisplayName("Una operación registrada en el proyecto se ejecuta y devuelve datos")
    void registeredOperationRunsAndReturnsData() throws Exception {
        String document = new String(
            new ClassPathResource("graphql/operations/CompaniesByAccount.graphql").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);

        graphql(token(), document, Map.of("publicAccountId", ACCOUNT), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors").doesNotExist())
            .andExpect(jsonPath("$.data.companiesByAccount").isArray());
    }

    @Test
    @DisplayName("Una consulta que no está registrada se rechaza sin ejecutarse")
    void unregisteredOperationIsRejectedWithoutRunning() throws Exception {
        graphql(token(), UNREGISTERED, null, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors[0].extensions.code").value("OPERATION_NOT_ALLOWED"))
            .andExpect(jsonPath("$.errors[0].message").value(REJECTION));
    }

    @Test
    @DisplayName("Mandar el hash de la propia consulta no abre la puerta")
    void ownHashDoesNotOpenTheDoor() throws Exception {
        graphql(token(), UNREGISTERED, null, sha256(UNREGISTERED))
            .andExpect(jsonPath("$.errors[0].extensions.code").value("OPERATION_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("La introspección del esquema se rechaza")
    void introspectionIsRejected() throws Exception {
        graphql(token(), "{ __schema { types { name } } }", null, null)
            .andExpect(jsonPath("$.errors[0].extensions.code").value("OPERATION_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("Sin token responde 401 antes de llegar a GraphQL")
    void withoutTokenRespondsUnauthorized() throws Exception {
        graphql(null, UNREGISTERED, null, null).andExpect(status().isUnauthorized());
    }

    private ResultActions graphql(String authorization, String query, Map<String, Object> variables, String hash) throws Exception {
        Map<String, Object> body = variables == null ? Map.of("query", query) : Map.of("query", query, "variables", variables);
        MockHttpServletRequestBuilder request = post("/graphql/v1").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        if (hash != null) {
            request.header("X-GraphQL-Operation-Hash", hash);
        }
        ResultActions result = mockMvc.perform(request);
        return result.andReturn().getRequest().isAsyncStarted() ? mockMvc.perform(asyncDispatch(result.andReturn())) : result;
    }

    private String token() {
        return "Bearer " + tokenService.issue(ACCOUNT, List.of());
    }

    private static String sha256(String document) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(document.getBytes(StandardCharsets.UTF_8)));
    }
}
