package com.jcode.jcomercy.companymanagement.domain.model.employee;

/** Qué pasó con el correo de una invitación: todavía no sale, salió, o el servidor de correo no lo aceptó. */
public enum EmailStatus {
    PENDING,
    SENT,
    FAILED
}
