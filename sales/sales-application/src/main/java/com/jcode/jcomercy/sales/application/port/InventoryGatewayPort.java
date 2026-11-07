package com.jcode.jcomercy.sales.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que Ventas le pide a Inventario. Ventas nunca toca las tablas de inventario: apartar, liberar, confirmar y devolver
 * stock se piden por su API, con el token de quien vende, así que Inventario decide con sus propios permisos. Cada
 * operación lleva una llave de idempotencia: repetirla con la misma llave no la ejecuta otra vez.
 *
 * <p>Los fallos se traducen a tres excepciones: {@code InsufficientStockException} (no alcanza el stock),
 * {@code InventoryRejectedException} (inventario dijo que no) e {@code InventoryUnavailableException} (no respondió;
 * puede reintentarse con la misma llave).
 */
public interface InventoryGatewayPort {

    Optional<ProductInfo> findProduct(Caller caller, UUID productId);

    void reserve(Caller caller, StockReservation request);

    void release(Caller caller, StockReference reference);

    void commit(Caller caller, StockReference reference);

    void returnStock(Caller caller, StockReference reference);

    /** Quién llama: su token, que se reenvía a inventario, y el identificador de seguimiento. */
    record Caller(String authorization, String correlationId) { }

    /** Lo que Ventas necesita saber de un producto de inventario. */
    record ProductInfo(UUID id, String companyId, String sku, String name, String stockUnitCode, String trackingMode,
                       String status) { }

    record StockReservation(String companyId, String branchId, UUID warehouseId, UUID productId, BigDecimal quantity,
                            String unitCode, String referenceType, String referenceId, Instant expiresAt,
                            String idempotencyKey) { }

    record StockReference(String companyId, String branchId, String referenceType, String referenceId, String reason,
                          String idempotencyKey) { }
}
