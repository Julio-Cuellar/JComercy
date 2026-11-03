package com.jcode.jcomercy.companymanagement.infra.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ERR-010: cada ruta REST bajo una cuenta o una empresa declara el permiso que exige, y ese permiso es el que el
 * contrato OpenAPI documenta (`x-required-permission`). Así el código y el contrato no se desfasan, y una ruta
 * nueva no puede quedar abierta por olvido.
 */
@SpringBootTest
class RequiredPermissionsContractTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("Toda ruta de cuentas y empresas declara el permiso que exige")
    void everyRouteUnderAnAccountOrCompanyDeclaresItsPermission() {
        Map<String, RequiresPermission> routes = protectedRoutes();
        Set<String> undeclared = new TreeSet<>();
        routes.forEach((route, declared) -> {
            if (declared == null) {
                undeclared.add(route);
            }
        });

        assertThat(routes).as("rutas bajo una cuenta o empresa").hasSizeGreaterThanOrEqualTo(10);
        assertThat(undeclared).as("rutas sin @RequiresPermission").isEmpty();
    }

    @Test
    @DisplayName("El permiso que declara cada ruta es el que documenta el contrato OpenAPI")
    @SuppressWarnings("unchecked")
    void theDeclaredPermissionIsTheOneInTheContract() throws Exception {
        Map<String, Object> contractOperations = contractOperations();
        Set<String> mismatches = new TreeSet<>();
        Set<String> compared = new TreeSet<>();
        protectedRoutes().forEach((route, declared) -> {
            if (declared == null) {
                return;
            }
            compared.add(route);
            Map<String, Object> operation = (Map<String, Object>) contractOperations.get(route);
            Object documented = operation == null ? null : operation.get("x-required-permission");
            if (!declared.value().equals(documented)) {
                mismatches.add(route + " declara " + declared.value() + " y el contrato dice " + documented);
            }
        });

        assertThat(compared).as("rutas comparadas con el contrato").hasSizeGreaterThanOrEqualTo(10);
        assertThat(mismatches).as("rutas cuyo permiso no coincide con el contrato").isEmpty();
    }

    /** Cada ruta como "MÉTODO /ruta" (variables unificadas como {}) con la anotación de su método, o null si no la tiene. */
    private Map<String, RequiresPermission> protectedRoutes() {
        Map<String, RequiresPermission> routes = new TreeMap<>();
        handlerMapping.getHandlerMethods().forEach((info, handler) -> {
            for (String pattern : patterns(info)) {
                if (pattern.startsWith("/api/v1/accounts/{") || pattern.startsWith("/api/v1/companies/{")) {
                    for (String method : methods(info)) {
                        routes.put(method + " " + normalize(pattern), annotationOf(handler));
                    }
                }
            }
        });
        return routes;
    }

    private static RequiresPermission annotationOf(HandlerMethod handler) {
        return handler.getMethodAnnotation(RequiresPermission.class);
    }

    private static Set<String> patterns(RequestMappingInfo info) {
        return info.getPathPatternsCondition() == null ? Set.of() : info.getPathPatternsCondition().getPatternValues();
    }

    private static Set<String> methods(RequestMappingInfo info) {
        Set<String> methods = new TreeSet<>();
        info.getMethodsCondition().getMethods().forEach(method -> methods.add(method.name()));
        return methods;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> contractOperations() throws Exception {
        Map<String, Object> spec;
        try (InputStream contract = new ClassPathResource("static/openapi-company-management-v1.yaml").getInputStream()) {
            spec = new Yaml().load(contract);
        }
        Map<String, Object> operations = new TreeMap<>();
        Set<String> httpMethods = Set.of("get", "post", "put", "patch", "delete");
        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        paths.forEach((path, item) -> ((Map<String, Object>) item).forEach((method, node) -> {
            if (httpMethods.contains(method)) {
                operations.put(method.toUpperCase(Locale.ROOT) + " " + normalize(path), node);
            }
        }));
        return operations;
    }

    private static String normalize(String path) {
        return path.replaceAll("\\{[^}]+}", "{}");
    }
}
