package com.jcode.jcomercy.companymanagement.infra.web.controller;

import com.jcode.jcomercy.companymanagement.application.dto.InvitationPreviewDto;
import com.jcode.jcomercy.companymanagement.application.service.EmployeeInvitationService;
import com.jcode.jcomercy.companymanagement.infra.security.JwtContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Donde la persona abre el enlace de su invitación. Son rutas públicas: quien las usa todavía no tiene acceso, y lo único
 * que lo identifica es el código del correo. Si el correo ya tiene cuenta, la aceptación exige la sesión de esa cuenta.
 * Nunca se escribe el código en el registro.
 */
@RestController
@RequestMapping("/api/v1/employee-invitations")
public class EmployeeInvitationController {
    private static final Logger log = LoggerFactory.getLogger(EmployeeInvitationController.class);

    private final EmployeeInvitationService invitations;

    public EmployeeInvitationController(EmployeeInvitationService invitations) {
        this.invitations = invitations;
    }

    /** La contraseña que elige quien no tiene cuenta; quien ya la tiene no manda nada y entra con su sesión. */
    public record AcceptRequest(String password) { }

    @GetMapping("/{code}")
    public ResponseEntity<InvitationPreviewDto> consult(@PathVariable String code) {
        return ResponseEntity.ok(invitations.consult(code));
    }

    @PostMapping("/{code}/acceptance")
    public ResponseEntity<Void> accept(
        @PathVariable String code,
        @RequestBody(required = false) AcceptRequest request,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        invitations.accept(code, request == null ? null : request.password(), callerAccount(), correlationId);
        log.info("Acceso activado por invitación correlationId={}", correlationId);
        return ResponseEntity.noContent().build();
    }

    /** La cuenta con la que se inició sesión, si trae el token de una cuenta; una sesión de empresa no cuenta. */
    private static String callerAccount() {
        JwtContext context = JwtContext.from(SecurityContextHolder.getContext().getAuthentication());
        return context.isAccountToken() ? context.accountId() : null;
    }
}
