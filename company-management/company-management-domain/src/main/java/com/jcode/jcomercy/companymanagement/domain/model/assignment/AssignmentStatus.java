package com.jcode.jcomercy.companymanagement.domain.model.assignment;

public enum AssignmentStatus {
    ACTIVE,
    SUSPENDED,
    /** Retirada: la fila se conserva (quién tuvo qué sucursal) pero ya no cuenta para nada; el contrato no la publica. */
    REMOVED
}
