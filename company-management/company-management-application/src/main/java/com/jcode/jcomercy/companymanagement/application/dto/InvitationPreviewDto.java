package com.jcode.jcomercy.companymanagement.application.dto;

import java.util.List;

/**
 * Lo que ve quien abre el enlace de su invitación, antes de activar su acceso. {@code accountExists} dice si ese correo
 * ya tiene cuenta: si es así, en vez de elegir contraseña debe iniciar sesión con ella.
 */
public record InvitationPreviewDto(String firstName, String companyName, List<String> roleNames, String email, boolean accountExists) { }
