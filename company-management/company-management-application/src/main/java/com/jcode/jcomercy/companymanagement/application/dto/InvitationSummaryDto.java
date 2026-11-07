package com.jcode.jcomercy.companymanagement.application.dto;

import java.time.Instant;

/**
 * Cómo va la invitación de una persona, como la ve RH: {@code status} es PENDING, ACCEPTED, CANCELLED o EXPIRED (la
 * pendiente cuya fecha ya pasó), y {@code emailStatus} es PENDING, SENT o FAILED («correo no enviado»).
 */
public record InvitationSummaryDto(String status, String emailStatus, Instant expiresAt) { }
