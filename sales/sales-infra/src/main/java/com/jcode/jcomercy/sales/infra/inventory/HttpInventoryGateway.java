package com.jcode.jcomercy.sales.infra.inventory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcode.jcomercy.sales.application.exception.InsufficientStockException;
import com.jcode.jcomercy.sales.application.exception.InventoryRejectedException;
import com.jcode.jcomercy.sales.application.exception.InventoryUnavailableException;
import com.jcode.jcomercy.sales.application.port.InventoryGatewayPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Habla con el inventario real por su API REST, con el token de quien vende, así que inventario decide con sus propios
 * permisos. Escribe los cuerpos de forma estable (la cantidad sin ceros de más ni notación científica) porque inventario
 * reconoce una operación repetida por su llave <i>y</i> por su cuerpo: si el cuerpo cambiara entre un intento y su
 * reintento, respondería 409. Cada falla se traduce a una excepción que el cobro sabe manejar: una respuesta de 5xx, un
 * corte de conexión o un tiempo agotado es «no disponible» (se puede reintentar con la misma llave); un 4xx, «rechazado»,
 * salvo el 422 de falta de stock.
 */
public class HttpInventoryGateway implements InventoryGatewayPort {
    private static final Logger log = LoggerFactory.getLogger(HttpInventoryGateway.class);
    private static final String INSUFFICIENT_STOCK = "insufficient available stock";

    private final RestClient client;
    private final ObjectMapper mapper = new ObjectMapper().enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);

    public HttpInventoryGateway(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeout.toMillis());
        factory.setReadTimeout((int) readTimeout.toMillis());
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public Optional<ProductInfo> findProduct(Caller caller, UUID productId) {
        Response response = call(HttpMethod.GET, "/api/v1/products/" + productId, caller, null, null);
        if (response.ok()) {
            JsonNode json = parse(response.body());
            return Optional.of(new ProductInfo(UUID.fromString(json.path("id").asText()), json.path("companyId").asText(),
                    json.path("sku").asText(), json.path("name").asText(), json.path("stockUnitCode").asText(),
                    json.path("trackingMode").asText(), json.path("status").asText()));
        }
        if (response.status() == 404) {
            return Optional.empty();
        }
        throw failure(response, false);
    }

    @Override
    public void reserve(Caller caller, StockReservation request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("warehouseId", request.warehouseId());
        body.put("productId", request.productId());
        body.put("quantity", request.quantity().stripTrailingZeros());
        body.put("unitCode", request.unitCode());
        body.put("referenceType", request.referenceType());
        body.put("referenceId", request.referenceId());
        body.put("expiresAt", request.expiresAt().toString());
        expectSuccess(call(HttpMethod.POST, branchPath(request.companyId(), request.branchId(), "stock/reservations"), caller,
                request.idempotencyKey(), body), true);
    }

    @Override
    public void release(Caller caller, StockReference reference) {
        post(caller, reference, "stock/reservations/release");
    }

    @Override
    public void commit(Caller caller, StockReference reference) {
        post(caller, reference, "stock/reservations/commit");
    }

    @Override
    public void returnStock(Caller caller, StockReference reference) {
        post(caller, reference, "stock/returns");
    }

    private void post(Caller caller, StockReference reference, String route) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("referenceType", reference.referenceType());
        body.put("referenceId", reference.referenceId());
        if (reference.reason() != null) {
            body.put("reason", reference.reason());
        }
        expectSuccess(call(HttpMethod.POST, branchPath(reference.companyId(), reference.branchId(), route), caller,
                reference.idempotencyKey(), body), false);
    }

    private static String branchPath(String companyId, String branchId, String route) {
        return "/api/v1/companies/" + companyId + "/branches/" + branchId + "/" + route;
    }

    private void expectSuccess(Response response, boolean reserving) {
        if (!response.ok()) {
            throw failure(response, reserving);
        }
    }

    /** Traduce una respuesta que no es de éxito. Un 5xx es transitorio; un 4xx es una respuesta de negocio de inventario. */
    private RuntimeException failure(Response response, boolean reserving) {
        String message = messageOf(response.body());
        log.warn("Inventario respondió {} mensaje={}", response.status(), message);
        if (response.status() >= 500) {
            return new InventoryUnavailableException("Inventario respondió " + response.status());
        }
        if (reserving && response.status() == 422 && message.startsWith(INSUFFICIENT_STOCK)) {
            return new InsufficientStockException(message);
        }
        return new InventoryRejectedException(message);
    }

    private Response call(HttpMethod method, String path, Caller caller, String idempotencyKey, Object body) {
        try {
            var spec = client.method(method).uri(path).headers(headers -> {
                if (caller.authorization() != null) headers.set("Authorization", caller.authorization());
                if (caller.correlationId() != null) headers.set("X-Correlation-Id", caller.correlationId());
                if (idempotencyKey != null) headers.set("Idempotency-Key", idempotencyKey);
            });
            if (body != null) {
                spec = spec.contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(body));
            }
            return spec.exchange((request, response) -> new Response(response.getStatusCode().value(),
                    new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (RestClientException | IOException e) {
            log.warn("No se pudo hablar con inventario: {}", e.toString());
            throw new InventoryUnavailableException("No pudimos comunicarnos con inventario", e);
        }
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (IOException e) {
            throw new InventoryUnavailableException("Inventario respondió algo que no se entiende", e);
        }
    }

    /** El mensaje de un error de inventario, que trae {@code message}; si no, el texto tal cual, acotado. */
    private String messageOf(String body) {
        try {
            JsonNode message = mapper.readTree(body).path("message");
            if (message.isTextual()) {
                return message.asText();
            }
        } catch (IOException ignored) {
            // No era JSON: se usa el texto.
        }
        String text = body == null ? "" : body.strip();
        return text.length() > 200 ? text.substring(0, 200) : text;
    }

    private record Response(int status, String body) {
        boolean ok() {
            return status >= 200 && status < 300;
        }
    }
}
