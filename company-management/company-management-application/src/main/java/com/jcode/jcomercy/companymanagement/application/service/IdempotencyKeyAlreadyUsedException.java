package com.jcode.jcomercy.companymanagement.application.service;

/** La clave de idempotencia ya fue reclamada por una operación anterior y este proceso no guarda su resultado. */
public class IdempotencyKeyAlreadyUsedException extends IllegalStateException {
    public IdempotencyKeyAlreadyUsedException() {
        super("La solicitud con Idempotency-Key ya fue procesada");
    }
}
