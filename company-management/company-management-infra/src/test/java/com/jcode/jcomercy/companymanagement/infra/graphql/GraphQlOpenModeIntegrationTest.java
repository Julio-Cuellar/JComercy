package com.jcode.jcomercy.companymanagement.infra.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.companymanagement.infra.security.JwtTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Con `graphql.security.persisted-only=false` (desarrollo local) cualquier operación corre, pero la introspección sigue apagada. */
@SpringBootTest(properties = "graphql.security.persisted-only=false")
@AutoConfigureMockMvc
class GraphQlOpenModeIntegrationTest {

    private static final String ACCOUNT = "acc_graphql_101";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService tokenService;

    @Test
    @DisplayName("En modo abierto cualquier operación se ejecuta")
    void anyOperationRunsInOpenMode() throws Exception {
        graphql("query { companiesByAccount(publicAccountId: \"" + ACCOUNT + "\") { publicCompanyId } }")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors").doesNotExist())
            .andExpect(jsonPath("$.data.companiesByAccount").isArray());
    }

    @Test
    @DisplayName("La introspección del esquema está apagada aunque el modo sea abierto")
    void introspectionIsDisabledEvenInOpenMode() throws Exception {
        graphql("{ __schema { types { name } } }")
            .andExpect(jsonPath("$.errors").isNotEmpty())
            .andExpect(jsonPath("$.data.__schema").doesNotExist());
    }

    private ResultActions graphql(String query) throws Exception {
        ResultActions result = mockMvc.perform(post("/graphql/v1").contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + tokenService.issue(ACCOUNT, List.of()))
            .content(objectMapper.writeValueAsString(Map.of("query", query))));
        return result.andReturn().getRequest().isAsyncStarted() ? mockMvc.perform(asyncDispatch(result.andReturn())) : result;
    }
}
