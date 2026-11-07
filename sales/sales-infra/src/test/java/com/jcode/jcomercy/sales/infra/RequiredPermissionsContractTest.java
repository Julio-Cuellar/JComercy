package com.jcode.jcomercy.sales.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Cada ruta que Ventas implementa exige el permiso que el contrato declara en {@code x-required-permission}: sin él
 * responde 403, y con él no. Así el contrato y el código no pueden separarse sin que esta prueba lo note.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RequiredPermissionsContractTest {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");
    private static final String COMPANY = "cmp_perm_contract";
    private static final String BRANCH = "br_perm_contract";
    private static final Pattern VARIABLE = Pattern.compile("\\{([^}]+)}");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("Cada ruta implementada declara su permiso en el contrato y lo exige")
    @SuppressWarnings("unchecked")
    void everyImplementedRouteEnforcesThePermissionOfTheContract() throws Exception {
        String generated = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        Map<String, Object> implementedPaths = (Map<String, Object>) objectMapper
            .readValue(generated, new TypeReference<Map<String, Object>>() { }).get("paths");
        Map<String, String> required = requiredPermissions();

        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (var path : implementedPaths.entrySet()) {
            for (String method : ((Map<String, Object>) path.getValue()).keySet()) {
                if (!HTTP_METHODS.contains(method)) continue;
                String key = method.toUpperCase(Locale.ROOT) + " " + normalize(path.getKey());
                String permission = required.get(key);
                if (permission == null) {
                    problems.add(key + ": el contrato no declara x-required-permission");
                    continue;
                }
                checked++;
                int without = call(method, path.getKey(), SalesTokens.bearer(COMPANY, List.of(BRANCH), "unrelated.permission"));
                int with = call(method, path.getKey(), SalesTokens.bearer(COMPANY, List.of(BRANCH), permission));
                if (without != 403) problems.add(key + ": sin " + permission + " respondió " + without + " y debía ser 403");
                if (with == 403 || with == 401) problems.add(key + ": con " + permission + " respondió " + with);
            }
        }

        assertThat(checked).as("rutas implementadas que se comprobaron").isGreaterThan(0);
        assertThat(problems).as("rutas cuyo permiso no coincide con el contrato").isEmpty();
    }

    private int call(String method, String template, String token) throws Exception {
        MockHttpServletRequestBuilder builder = request(org.springframework.http.HttpMethod.valueOf(method.toUpperCase(Locale.ROOT)),
            concrete(template)).header("Authorization", token).header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON).content(validBody(method.toUpperCase(Locale.ROOT) + " " + normalize(template)));
        return mockMvc.perform(builder).andReturn().getResponse().getStatus();
    }

    /**
     * Spring valida el cuerpo antes de entrar al controlador, así que para ver el 403 del permiso la petición debe traer
     * un cuerpo válido. Cada ruta con cuerpo agrega el suyo aquí; las demás reciben un objeto vacío.
     */
    private static String validBody(String operation) {
        return switch (operation) {
            case "PUT /api/v1/companies/{}/products/{}/price" -> "{\"amount\":10.00,\"currency\":\"MXN\"}";
            default -> "{}";
        };
    }

    private static String concrete(String template) {
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder url = new StringBuilder();
        while (matcher.find()) {
            String value = switch (matcher.group(1)) {
                case "companyId" -> COMPANY;
                case "branchId" -> BRANCH;
                default -> UUID.randomUUID().toString();
            };
            matcher.appendReplacement(url, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(url);
        return url.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> requiredPermissions() throws Exception {
        Map<String, String> required = new TreeMap<>();
        try (InputStream contract = new ClassPathResource("static/openapi-sales-v1.yaml").getInputStream()) {
            Map<String, Object> spec = new Yaml().load(contract);
            ((Map<String, Object>) spec.get("paths")).forEach((path, item) -> ((Map<String, Object>) item).forEach((method, operation) -> {
                if (HTTP_METHODS.contains(method) && operation instanceof Map<?, ?> map && map.get("x-required-permission") != null) {
                    required.put(method.toUpperCase(Locale.ROOT) + " " + normalize(path), map.get("x-required-permission").toString());
                }
            }));
        }
        return required;
    }

    private static String normalize(String path) {
        return path.replaceAll("\\{[^}]+}", "{}");
    }
}
