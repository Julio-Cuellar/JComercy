package com.jcode.jcomercy.companymanagement.application.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** In-process registry used until the durable idempotency table is introduced. */
public class IdempotencyRegistry {
    private final Map<String, Object> completed = new ConcurrentHashMap<>();
    private final IdempotencyStore store;

    public IdempotencyRegistry() { this(null); }
    public IdempotencyRegistry(IdempotencyStore store) { this.store = store; }

    @SuppressWarnings("unchecked")
    public synchronized <T> T execute(String operation, String key, Supplier<T> action) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key es obligatorio");
        }
        String registryKey = operation + ":" + key;
        Object previous = completed.get(registryKey);
        if (previous != null) return (T) previous;
        if (store != null && !store.claim(operation, key)) {
            throw new IllegalStateException("La solicitud con Idempotency-Key ya fue procesada");
        }
        try {
            T result = action.get();
            completed.put(registryKey, result);
            return result;
        } catch (RuntimeException ex) {
            if (store != null) store.release(operation, key);
            throw ex;
        }
    }
}
