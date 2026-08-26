package com.jcode.jcomercy.companymanagement.domain.exception;

public class DuplicateResourceException extends DomainException {
    public DuplicateResourceException(String resourceName, String field, String value) {
        super(String.format("El recurso %s con %s '%s' ya existe.", resourceName, field, value));
    }
}
