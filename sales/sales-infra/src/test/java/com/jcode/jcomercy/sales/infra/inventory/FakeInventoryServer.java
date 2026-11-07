package com.jcode.jcomercy.sales.infra.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Un inventario de mentira por HTTP, para probar a Ventas sin levantar el módulo de inventario. Se comporta como el real
 * en lo que importa: repetir una operación con la misma llave y el mismo cuerpo devuelve la respuesta guardada sin
 * ejecutarla otra vez, y reusar la llave con otro cuerpo responde 409; al apartar descuenta lo disponible, y devuelve 422
 * con el mensaje del inventario real cuando no alcanza.
 */
public final class FakeInventoryServer implements AutoCloseable {

    /** Una petición que llegó, con sus encabezados en minúsculas. */
    public record Recorded(String method, String path, Map<String, String> headers, String body) { }

    private static final Pattern PRODUCT = Pattern.compile("/api/v1/products/([^/]+)");
    private static final Pattern STOCK = Pattern.compile(
        "/api/v1/companies/([^/]+)/branches/([^/]+)/stock/(reservations/release|reservations/commit|reservations|returns)");

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpServer server;
    public final List<Recorded> requests = new CopyOnWriteArrayList<>();

    private final Map<UUID, Map<String, String>> products = new HashMap<>();
    private final Map<UUID, BigDecimal> available = new HashMap<>();
    private final Map<String, String[]> replies = new HashMap<>();   // llave -> {hash, estado, cuerpo}
    private final Map<String, BigDecimal> reservedByReference = new HashMap<>();
    private final Map<String, UUID> productByReference = new HashMap<>();
    private final Map<String, BigDecimal> committedByReference = new HashMap<>();
    private volatile int failAllWith;
    private volatile String failPathContaining;
    private volatile int failPathWith;
    private volatile long delayMillis;

    private FakeInventoryServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();
    }

    public static FakeInventoryServer start() throws IOException {
        return new FakeInventoryServer();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public synchronized void product(UUID id, String companyId, String sku, String name, String stock) {
        product(id, companyId, sku, name, "NONE", "ACTIVE", stock);
    }

    public synchronized void product(UUID id, String companyId, String sku, String name, String tracking, String status, String stock) {
        products.put(id, Map.of("id", id.toString(), "companyId", companyId, "sku", sku, "name", name, "stockUnitCode", "PZA",
            "trackingMode", tracking, "status", status));
        available.put(id, new BigDecimal(stock));
    }

    public synchronized BigDecimal available(UUID id) {
        return available.get(id);
    }

    /** Todas las peticiones responden con ese estado, como un inventario caído. 0 lo apaga. */
    public void failAllWith(int status) {
        this.failAllWith = status;
    }

    /** Las peticiones cuya ruta contiene ese texto responden con ese estado. */
    public void failPath(String containing, int status) {
        this.failPathContaining = containing;
        this.failPathWith = status;
    }

    public void healPaths() {
        this.failPathContaining = null;
    }

    public void delay(long millis) {
        this.delayMillis = millis;
    }

    public List<Recorded> requestsTo(String pathContaining) {
        return requests.stream().filter(r -> r.path().contains(pathContaining)).toList();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // ---- Manejo de peticiones ----

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> headers = new HashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.get(0)));
        requests.add(new Recorded(exchange.getRequestMethod(), path, headers, body));
        try {
            if (delayMillis > 0) Thread.sleep(delayMillis);
            if (failAllWith != 0) {
                reply(exchange, failAllWith, "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"Error interno del servidor\"}");
            } else if (failPathContaining != null && path.contains(failPathContaining)) {
                reply(exchange, failPathWith, "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"Error interno del servidor\"}");
            } else {
                dispatch(exchange, path, body, headers);
            }
        } catch (Exception e) {
            reply(exchange, 500, "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"" + e + "\"}");
        }
    }

    private synchronized void dispatch(HttpExchange exchange, String path, String body, Map<String, String> headers) throws Exception {
        Matcher product = PRODUCT.matcher(path);
        if ("GET".equals(exchange.getRequestMethod()) && product.matches()) {
            var found = products.get(UUID.fromString(product.group(1)));
            if (found == null) {
                reply(exchange, 404, "{\"code\":\"INVENTORY_OPERATION_REJECTED\",\"message\":\"product not found\"}");
            } else {
                reply(exchange, 200, mapper.writeValueAsString(found));
            }
            return;
        }
        Matcher stock = STOCK.matcher(path);
        if ("POST".equals(exchange.getRequestMethod()) && stock.matches()) {
            String key = headers.get("idempotency-key");
            if (key == null || key.isBlank()) {
                reply(exchange, 400, "{\"code\":\"MALFORMED_REQUEST\",\"message\":\"Solicitud inválida\"}");
                return;
            }
            String scope = headers.get("authorization") + "|" + key;
            String hash = Integer.toHexString(body.hashCode());
            String[] previous = replies.get(scope);
            if (previous != null) {
                if (!previous[0].equals(hash)) {
                    reply(exchange, 409, "{\"code\":\"IDEMPOTENCY_KEY_CONFLICT\",\"message\":\"Idempotency key reused with different request payload\"}");
                } else {
                    reply(exchange, Integer.parseInt(previous[1]), previous[2]);
                }
                return;
            }
            JsonNode json = mapper.readTree(body);
            Object[] result = execute(stock.group(3), json);
            int status = (int) result[0];
            String response = (String) result[1];
            if (status < 500) replies.put(scope, new String[] {hash, Integer.toString(status), response});
            reply(exchange, status, response);
            return;
        }
        reply(exchange, 404, "{\"code\":\"NOT_FOUND\",\"message\":\"La dirección solicitada no existe.\"}");
    }

    private Object[] execute(String operation, JsonNode json) {
        String reference = json.path("referenceId").asText().toUpperCase();
        switch (operation) {
            case "reservations" -> {
                UUID productId = UUID.fromString(json.path("productId").asText());
                BigDecimal quantity = new BigDecimal(json.path("quantity").asText());
                BigDecimal free = available.getOrDefault(productId, BigDecimal.ZERO);
                if (free.compareTo(quantity) < 0) {
                    return new Object[] {422, "{\"code\":\"INVENTORY_DOMAIN_RULE_VIOLATION\",\"message\":\"insufficient available stock for reservation\"}"};
                }
                available.put(productId, free.subtract(quantity));
                reservedByReference.put(reference, quantity);
                productByReference.put(reference, productId);
                return new Object[] {201, "{\"reservations\":[{\"status\":\"ACTIVE\"}],\"available\":" + available.get(productId) + "}"};
            }
            case "reservations/release" -> {
                BigDecimal quantity = reservedByReference.remove(reference);
                if (quantity == null) {
                    return new Object[] {404, "{\"code\":\"INVENTORY_OPERATION_REJECTED\",\"message\":\"no active reservation found for this reference\"}"};
                }
                available.merge(productByReference.get(reference), quantity, BigDecimal::add);
                return new Object[] {200, "{\"reservations\":[{\"status\":\"RELEASED\"}]}"};
            }
            case "reservations/commit" -> {
                BigDecimal quantity = reservedByReference.remove(reference);
                if (quantity == null) {
                    return new Object[] {404, "{\"code\":\"INVENTORY_OPERATION_REJECTED\",\"message\":\"no active reservation found for this reference\"}"};
                }
                committedByReference.put(reference, quantity);
                return new Object[] {200, "{\"reservations\":[{\"status\":\"COMMITTED\"}]}"};
            }
            default -> {
                BigDecimal quantity = committedByReference.remove(reference);
                if (quantity == null) {
                    return new Object[] {404, "{\"code\":\"INVENTORY_OPERATION_REJECTED\",\"message\":\"no stock issue found for this reference\"}"};
                }
                available.merge(productByReference.get(reference), quantity, BigDecimal::add);
                return new Object[] {201, "{\"returned\":[{\"type\":\"RETURN\"}]}"};
            }
        }
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
