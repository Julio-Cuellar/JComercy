package com.jcode.jcomercy.inventory.application.exception;

public class InventoryApplicationException extends RuntimeException {

    public enum Kind { NOT_FOUND, CONFLICT }

    private final Kind kind;

    /** Regla de negocio incumplida; la capa web la responde como conflicto (409). */
    public InventoryApplicationException(String message) {
        this(Kind.CONFLICT, message);
    }

    private InventoryApplicationException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    /** El recurso solicitado no existe; la capa web lo responde como no encontrado (404). */
    public static InventoryApplicationException notFound(String message) {
        return new InventoryApplicationException(Kind.NOT_FOUND, message);
    }

    public Kind kind() {
        return kind;
    }
}
