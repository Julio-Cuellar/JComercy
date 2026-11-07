package com.jcode.jcomercy.sales.application.service;

import com.jcode.jcomercy.sales.application.exception.InsufficientStockException;
import com.jcode.jcomercy.sales.application.exception.InventoryRejectedException;
import com.jcode.jcomercy.sales.application.exception.InventoryUnavailableException;
import com.jcode.jcomercy.sales.application.port.InventoryGatewayPort;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Un inventario de mentira para probar el cobro. Se comporta como el real en lo que importa: repetir una operación con
 * la misma llave no la ejecuta otra vez (por eso {@link #effects} cuenta lo que de verdad pasó y {@link #calls} todo lo
 * que se pidió), y se le pueden provocar fallas.
 */
final class FakeInventory implements InventoryGatewayPort {

    /** Cada petición que llegó, repetida o no, con su llave. */
    final List<String> calls = new ArrayList<>();
    /** Lo que de verdad se ejecutó: una repetición con la misma llave no aparece aquí. */
    final List<String> effects = new ArrayList<>();
    final List<StockReservation> reservations = new ArrayList<>();

    private final Map<UUID, ProductInfo> products = new HashMap<>();
    private final Map<UUID, BigDecimal> available = new HashMap<>();
    private final Set<String> seenKeys = new HashSet<>();
    private boolean unavailable;
    private int failCommitsAfter = Integer.MAX_VALUE;
    private int commitsSoFar;
    private String rejectReserveWith;

    void product(UUID id, String sku, String name, String tracking, String status, String stock) {
        products.put(id, new ProductInfo(id, "cmp_1", sku, name, "PZA", tracking, status));
        available.put(id, new BigDecimal(stock));
    }

    void unavailable(boolean value) {
        this.unavailable = value;
    }

    /** Los commits a partir del número n+1 fallan como si inventario no respondiera. */
    void failCommitsAfter(int n) {
        this.failCommitsAfter = n;
        this.commitsSoFar = 0;
    }

    void healCommits() {
        this.failCommitsAfter = Integer.MAX_VALUE;
    }

    void rejectReservations(String message) {
        this.rejectReserveWith = message;
    }

    @Override
    public Optional<ProductInfo> findProduct(Caller caller, UUID productId) {
        calls.add("product " + productId);
        if (unavailable) throw new InventoryUnavailableException("inventario no responde");
        return Optional.ofNullable(products.get(productId));
    }

    @Override
    public void reserve(Caller caller, StockReservation request) {
        calls.add("reserve " + request.referenceId() + " key=" + request.idempotencyKey());
        reservations.add(request);
        if (unavailable) throw new InventoryUnavailableException("inventario no responde");
        if (rejectReserveWith != null) throw new InventoryRejectedException(rejectReserveWith);
        if (!seenKeys.add(request.idempotencyKey())) return;
        BigDecimal free = available.getOrDefault(request.productId(), BigDecimal.ZERO);
        if (free.compareTo(request.quantity()) < 0) {
            seenKeys.remove(request.idempotencyKey());
            throw new InsufficientStockException("insufficient available stock for reservation");
        }
        available.put(request.productId(), free.subtract(request.quantity()));
        effects.add("reserve " + request.referenceId());
    }

    @Override
    public void release(Caller caller, StockReference reference) {
        calls.add("release " + reference.referenceId() + " key=" + reference.idempotencyKey());
        if (!seenKeys.add(reference.idempotencyKey())) return;
        effects.add("release " + reference.referenceId());
    }

    @Override
    public void commit(Caller caller, StockReference reference) {
        calls.add("commit " + reference.referenceId() + " key=" + reference.idempotencyKey());
        if (unavailable) throw new InventoryUnavailableException("inventario no responde");
        if (commitsSoFar >= failCommitsAfter && !seenKeys.contains(reference.idempotencyKey())) {
            throw new InventoryUnavailableException("inventario no responde");
        }
        if (!seenKeys.add(reference.idempotencyKey())) return;
        commitsSoFar++;
        effects.add("commit " + reference.referenceId());
    }

    @Override
    public void returnStock(Caller caller, StockReference reference) {
        calls.add("return " + reference.referenceId() + " key=" + reference.idempotencyKey());
        if (unavailable) throw new InventoryUnavailableException("inventario no responde");
        if (!seenKeys.add(reference.idempotencyKey())) return;
        effects.add("return " + reference.referenceId());
    }
}
