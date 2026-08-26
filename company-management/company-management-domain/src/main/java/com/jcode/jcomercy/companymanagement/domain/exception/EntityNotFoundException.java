package com.jcode.jcomercy.companymanagement.domain.exception;

public class EntityNotFoundException extends DomainException {
    public EntityNotFoundException(String entityName, String identifier) {
        super(String.format("El recurso %s con identificador '%s' no fue encontrado.", entityName, identifier));
    }
}
