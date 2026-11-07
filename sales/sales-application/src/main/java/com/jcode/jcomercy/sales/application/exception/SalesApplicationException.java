package com.jcode.jcomercy.sales.application.exception;

/** Un caso de uso de ventas que no se puede completar: lo pedido no existe, o el estado actual no lo permite. */
public class SalesApplicationException extends RuntimeException {

    public enum Kind { NOT_FOUND, CONFLICT }

    private final Kind kind;

    public SalesApplicationException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public static SalesApplicationException notFound(String message) {
        return new SalesApplicationException(Kind.NOT_FOUND, message);
    }

    public static SalesApplicationException conflict(String message) {
        return new SalesApplicationException(Kind.CONFLICT, message);
    }

    public Kind kind() {
        return kind;
    }
}
