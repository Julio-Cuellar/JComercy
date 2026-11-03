package com.jcode.jcomercy.companymanagement.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El contrato OpenAPI debe describir cada ruta que el módulo expone de verdad. Solo se exige en ese sentido: el
 * contrato documenta también rutas anticipadas que todavía no existen en el código.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Cada operación implementada está documentada en openapi-company-management-v1.yaml")
    void everyImplementedOperationIsDocumentedInTheContract() throws Exception {
        Map<String, Map<String, Object>> implemented = operations(generatedSpec());
        Set<String> missing = new TreeSet<>(implemented.keySet());
        missing.removeAll(operations(contractSpec()).keySet());

        assertThat(implemented).as("operaciones que genera springdoc").isNotEmpty();
        assertThat(missing).as("operaciones implementadas que faltan en el contrato").isEmpty();
    }

    @Test
    @DisplayName("Las operaciones implementadas con cuerpo declaran 400 y 415 en el contrato")
    @SuppressWarnings("unchecked")
    void operationsWithABodyDeclareMalformedRequestResponses() throws Exception {
        Map<String, Map<String, Object>> implemented = operations(generatedSpec());
        Map<String, Map<String, Object>> documented = operations(contractSpec());

        Set<String> incomplete = new TreeSet<>();
        long withBody = implemented.entrySet().stream().filter(entry -> entry.getValue().containsKey("requestBody")).count();
        implemented.forEach((operation, node) -> {
            if (!node.containsKey("requestBody") || !documented.containsKey(operation)) {
                return;
            }
            Map<String, Object> responses = (Map<String, Object>) documented.get(operation).get("responses");
            for (String code : List.of("400", "415")) {
                if (responses == null || !responses.containsKey(code)) {
                    incomplete.add(operation + " sin " + code);
                }
            }
        });

        assertThat(withBody).as("operaciones implementadas con cuerpo").isPositive();
        assertThat(incomplete).as("respuestas que faltan en operaciones con cuerpo").isEmpty();
    }

    private Map<String, Object> generatedSpec() throws Exception {
        String generated = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(generated, new TypeReference<Map<String, Object>>() { });
    }

    private static Map<String, Object> contractSpec() throws Exception {
        try (InputStream contract = new ClassPathResource("static/openapi-company-management-v1.yaml").getInputStream()) {
            return new Yaml().load(contract);
        }
    }

    /** Devuelve cada operación como "MÉTODO /ruta" (variables de ruta unificadas como {}) con su nodo del contrato. */
    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> operations(Map<String, Object> spec) {
        Map<String, Map<String, Object>> operations = new TreeMap<>();
        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        paths.forEach((path, item) -> ((Map<String, Object>) item).forEach((method, node) -> {
            if (HTTP_METHODS.contains(method)) {
                operations.put(method.toUpperCase(Locale.ROOT) + " " + path.replaceAll("\\{[^}]+}", "{}"),
                    (Map<String, Object>) node);
            }
        }));
        return operations;
    }
}
