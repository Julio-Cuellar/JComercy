package com.jcode.jcomercy.sales.application.exception;

/** Inventario no tiene disponible lo que se quiso apartar. */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
