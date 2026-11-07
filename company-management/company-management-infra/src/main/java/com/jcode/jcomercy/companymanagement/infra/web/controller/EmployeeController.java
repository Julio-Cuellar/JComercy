package com.jcode.jcomercy.companymanagement.infra.web.controller;

import com.jcode.jcomercy.companymanagement.application.dto.EmployeeDto;
import com.jcode.jcomercy.companymanagement.application.dto.EmployeeInput;
import com.jcode.jcomercy.companymanagement.application.dto.PageDto;
import com.jcode.jcomercy.companymanagement.application.service.EmployeeApplicationService;
import com.jcode.jcomercy.companymanagement.application.service.EmployeeInvitationService;
import com.jcode.jcomercy.companymanagement.domain.exception.UnauthorizedAccessException;
import com.jcode.jcomercy.companymanagement.infra.security.EffectivePermissions;
import com.jcode.jcomercy.companymanagement.infra.security.RequiresPermission;
import com.jcode.jcomercy.core.authorization.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.Principal;
import java.util.Set;
import java.util.function.Predicate;

/** El alta de las personas de la empresa por RH. Los datos personales (RFC, CURP, correo) nunca se escriben en el registro. */
@RestController
@RequestMapping("/api/v1/companies/{publicCompanyId}/employees")
public class EmployeeController {
    private static final Logger log = LoggerFactory.getLogger(EmployeeController.class);

    private final EmployeeApplicationService employees;
    private final EmployeeInvitationService invitations;
    private final EffectivePermissions effectivePermissions;

    public EmployeeController(EmployeeApplicationService employees, EmployeeInvitationService invitations,
                              EffectivePermissions effectivePermissions) {
        this.employees = employees;
        this.invitations = invitations;
        this.effectivePermissions = effectivePermissions;
    }

    @PostMapping
    @RequiresPermission(value = "employees.create", action = "registrar a una persona del equipo")
    public ResponseEntity<EmployeeDto> register(
        @PathVariable String publicCompanyId,
        @RequestBody EmployeeInput request,
        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
        Principal principal,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        String actor = actor(principal);
        EmployeeDto registered = employees.register(publicCompanyId, request, canGive(actor, publicCompanyId), actor, correlationId, idempotencyKey);
        // El correo sale al confirmar el alta: se vuelve a leer para que RH vea si salió o no.
        EmployeeDto created = employees.get(publicCompanyId, registered.employeeId());
        log.info("Persona registrada employeeId={} companyId={} actor={}", created.employeeId(), publicCompanyId, actor);
        return ResponseEntity.created(URI.create("/api/v1/companies/" + publicCompanyId + "/employees/" + created.employeeId()))
            .header("ETag", "\"" + created.version() + "\"").body(created);
    }

    @GetMapping
    @RequiresPermission(value = "employees.read", action = "consultar al equipo registrado")
    public ResponseEntity<PageDto<EmployeeDto>> list(
        @PathVariable String publicCompanyId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) String after
    ) {
        return ResponseEntity.ok(employees.list(publicCompanyId, status, limit, after));
    }

    @GetMapping("/{employeeId}")
    @RequiresPermission(value = "employees.read", action = "consultar al equipo registrado")
    public ResponseEntity<EmployeeDto> get(@PathVariable String publicCompanyId, @PathVariable String employeeId) {
        EmployeeDto employee = employees.get(publicCompanyId, employeeId);
        return ResponseEntity.ok().header("ETag", "\"" + employee.version() + "\"").body(employee);
    }

    @PatchMapping("/{employeeId}")
    @RequiresPermission(value = "employees.update", action = "corregir los datos de una persona del equipo")
    public ResponseEntity<EmployeeDto> correct(
        @PathVariable String publicCompanyId,
        @PathVariable String employeeId,
        @RequestBody EmployeeInput request,
        Principal principal
    ) {
        String actor = actor(principal);
        EmployeeDto corrected = employees.correct(publicCompanyId, employeeId, request, canGive(actor, publicCompanyId));
        log.info("Datos de persona corregidos employeeId={} companyId={} actor={}", employeeId, publicCompanyId, actor);
        return ResponseEntity.ok().header("ETag", "\"" + corrected.version() + "\"").body(corrected);
    }

    /** Manda un enlace nuevo a una persona que aún no activa su acceso; el anterior deja de servir. */
    @PostMapping("/{employeeId}/invitation")
    @RequiresPermission(value = "employees.invite", action = "reenviar invitaciones")
    public ResponseEntity<EmployeeDto> resendInvitation(
        @PathVariable String publicCompanyId,
        @PathVariable String employeeId,
        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
        Principal principal,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        String actor = actor(principal);
        invitations.resend(publicCompanyId, employeeId, actor, correlationId, idempotencyKey);
        log.info("Invitación reenviada employeeId={} companyId={} actor={}", employeeId, publicCompanyId, actor);
        EmployeeDto employee = employees.get(publicCompanyId, employeeId);
        return ResponseEntity.ok().header("ETag", "\"" + employee.version() + "\"").body(employee);
    }

    /** Cancela la invitación vigente; la persona sigue pendiente y se le puede volver a invitar. */
    @DeleteMapping("/{employeeId}/invitation")
    @RequiresPermission(value = "employees.invite", action = "cancelar invitaciones")
    public ResponseEntity<Void> cancelInvitation(
        @PathVariable String publicCompanyId,
        @PathVariable String employeeId,
        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
        Principal principal,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        String actor = actor(principal);
        invitations.cancel(publicCompanyId, employeeId, actor, correlationId, idempotencyKey);
        log.info("Invitación cancelada employeeId={} companyId={} actor={}", employeeId, publicCompanyId, actor);
        return ResponseEntity.noContent().build();
    }

    /** Un rol solo lo da quien tiene todos sus permisos hoy en esta empresa. */
    private Predicate<String> canGive(String actor, String companyId) {
        Set<String> granted = effectivePermissions.inCompany(actor, companyId);
        return code -> Permissions.grants(granted, code);
    }

    private static String actor(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new UnauthorizedAccessException("Autenticación requerida");
        }
        return principal.getName();
    }
}
