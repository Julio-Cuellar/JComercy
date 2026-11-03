package com.jcode.jcomercy.companymanagement.application.service;

/** La operación exige una clave de idempotencia y quien llama no mandó una con valor. */
public class IdempotencyKeyRequiredException extends IllegalArgumentException {
    public IdempotencyKeyRequiredException() {
        super("Idempotency-Key es obligatorio");
    }
}
