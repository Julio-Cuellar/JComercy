package com.jcode.jcomercy.sales.application.exception;

/** Inventario respondió que no: falta un permiso, la referencia ya existe o el almacén no está disponible. */
public class InventoryRejectedException extends RuntimeException {
    public InventoryRejectedException(String message) {
        super(message);
    }
}
