package com.jcode.jcomercy.companymanagement.domain.exception;

/** Lo que se pidió está mal formado (un filtro desconocido, un límite fuera de rango, un cursor viejo): responde 400. */
public class InvalidRequestException extends DomainException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
