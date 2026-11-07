package com.jcode.jcomercy.sales.infra;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El contrato OpenAPI debe describir cada ruta que el módulo expone de verdad. */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("Cada operación implementada está documentada en openapi-sales-v1.yaml")
    void everyImplementedOperationIsDocumentedInTheContract() throws Exception {
        String generated = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        Set<String> implemented = operations(objectMapper.readValue(generated, new TypeReference<Map<String, Object>>() { }));
        Set<String> documented;
        try (InputStream contract = new ClassPathResource("static/openapi-sales-v1.yaml").getInputStream()) {
            documented = operations(new Yaml().<Map<String, Object>>load(contract));
        }

        Set<String> missing = new TreeSet<>(implemented);
        missing.removeAll(documented);

        assertThat(implemented).as("operaciones que genera springdoc").isNotEmpty();
        assertThat(missing).as("operaciones implementadas que faltan en el contrato").isEmpty();
    }

    /** Devuelve "MÉTODO /ruta" por operación, con los nombres de las variables de ruta unificados como {}. */
    @SuppressWarnings("unchecked")
    private static Set<String> operations(Map<String, Object> spec) {
        Set<String> operations = new TreeSet<>();
        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        paths.forEach((path, item) -> ((Map<String, Object>) item).keySet().stream()
            .filter(HTTP_METHODS::contains)
            .forEach(method -> operations.add(method.toUpperCase(Locale.ROOT) + " " + path.replaceAll("\\{[^}]+}", "{}"))));
        return operations;
    }
}
