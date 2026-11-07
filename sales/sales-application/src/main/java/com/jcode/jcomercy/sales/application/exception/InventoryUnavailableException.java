package com.jcode.jcomercy.sales.application.exception;

/** Inventario no respondió (caído, lento o con un error del servidor). La operación puede reintentarse con la misma llave. */
public class InventoryUnavailableException extends RuntimeException {
    public InventoryUnavailableException(String message) {
        super(message);
    }

    public InventoryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
