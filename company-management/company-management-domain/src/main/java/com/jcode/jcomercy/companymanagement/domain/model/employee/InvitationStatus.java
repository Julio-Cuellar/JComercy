package com.jcode.jcomercy.companymanagement.domain.model.employee;

/**
 * Cómo va una invitación. «Vencida» no se guarda: una invitación pendiente cuya fecha ya pasó se muestra como vencida y
 * no sirve, sin que nadie tenga que marcarla.
 */
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    CANCELLED
}
