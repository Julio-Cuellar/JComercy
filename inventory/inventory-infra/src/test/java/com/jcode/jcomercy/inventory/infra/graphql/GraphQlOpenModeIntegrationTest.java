package com.jcode.jcomercy.inventory.infra.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Date;
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

    private static final String SECRET = "local-only-change-me-012345678901234567890123456789";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("En modo abierto cualquier operación se ejecuta")
    void anyOperationRunsInOpenMode() throws Exception {
        graphql("query { unitsOfMeasure { code } }")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors").doesNotExist())
            .andExpect(jsonPath("$.data.unitsOfMeasure").isNotEmpty());
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
            .header("Authorization", token())
            .content(objectMapper.writeValueAsString(Map.of("query", query))));
        return result.andReturn().getRequest().isAsyncStarted() ? mockMvc.perform(asyncDispatch(result.andReturn())) : result;
    }

    private static String token() {
        return "Bearer " + Jwts.builder()
            .subject("user-graphql-101")
            .issuer("jcomercy")
            .claim("companyIds", List.of("cmp_graphql_101"))
            .claim("branchIds", List.of("br_graphql_101"))
            .claim("permissions", "INVENTORY_READ")
            .expiration(new Date(System.currentTimeMillis() + 3600_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }
}
