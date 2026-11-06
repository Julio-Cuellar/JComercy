package com.jcode.jcomercy.companymanagement.domain.model.role;

public enum RoleStatus {
    ACTIVE,
    INACTIVE,
    /** Borrado lógico: el rol se conserva en la base pero ya no existe para nadie; el contrato nunca lo publica. */
    DELETED
}
