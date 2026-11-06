package com.jcode.jcomercy.companymanagement.infra.web.controller;

import com.jcode.jcomercy.companymanagement.application.dto.AssignmentDto;
import com.jcode.jcomercy.companymanagement.application.dto.PageDto;
import com.jcode.jcomercy.companymanagement.application.service.AssignmentApplicationService;
import com.jcode.jcomercy.companymanagement.domain.exception.InvalidRequestException;
import com.jcode.jcomercy.companymanagement.domain.exception.UnauthorizedAccessException;
import com.jcode.jcomercy.companymanagement.infra.security.RequiresPermission;
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
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.Principal;
import java.util.List;

/** Las sucursales de una persona de la empresa: ver, agregar, suspender o reactivar y quitar. */
@RestController
@RequestMapping("/api/v1/companies/{publicCompanyId}/memberships/{membershipId}/assignments")
public class BranchAssignmentController {
    private static final Logger log = LoggerFactory.getLogger(BranchAssignmentController.class);

    private final AssignmentApplicationService assignments;

    public BranchAssignmentController(AssignmentApplicationService assignments) {
        this.assignments = assignments;
    }

    /**
     * La sucursal y su estado. {@code roleIds} y {@code permissions} existen en el contrato, pero el modelo todavía no da
     * roles ni permisos distintos por sucursal: solo se aceptan si no piden nada distinto de lo que la persona ya tiene.
     */
    public record AssignmentRequest(String branchId, List<String> roleIds, List<String> permissions, String status) { }

    @GetMapping
    @RequiresPermission(value = "assignments.read", action = "consultar las sucursales de una persona")
    public ResponseEntity<PageDto<AssignmentDto>> list(@PathVariable String publicCompanyId, @PathVariable String membershipId) {
        return ResponseEntity.ok(assignments.list(publicCompanyId, membershipId));
    }

    @PostMapping
    @RequiresPermission(value = "assignments.create", action = "agregar sucursales a una persona")
    public ResponseEntity<AssignmentDto> create(
        @PathVariable String publicCompanyId,
        @PathVariable String membershipId,
        @RequestBody AssignmentRequest request,
        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
        Principal principal,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        String actor = actor(principal);
        AssignmentDto created = assignments.create(publicCompanyId, membershipId, request.branchId(), request.roleIds(),
            request.permissions(), request.status(), actor, correlationId, idempotencyKey);
        log.info("Sucursal agregada assignmentId={} membershipId={} companyId={} actor={}", created.assignmentId(), membershipId, publicCompanyId, actor);
        return ResponseEntity.created(URI.create("/api/v1/companies/" + publicCompanyId + "/memberships/" + membershipId
                + "/assignments/" + created.assignmentId()))
            .header("ETag", "\"" + created.version() + "\"").body(created);
    }

    @PatchMapping("/{assignmentId}")
    @RequiresPermission(value = "assignments.update", action = "cambiar las sucursales de una persona")
    public ResponseEntity<AssignmentDto> update(
        @PathVariable String publicCompanyId,
        @PathVariable String membershipId,
        @PathVariable String assignmentId,
        @RequestBody AssignmentRequest request,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        Principal principal,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        String actor = actor(principal);
        AssignmentDto updated = assignments.update(publicCompanyId, membershipId, assignmentId, request.branchId(), request.status(),
            versionOf(ifMatch), actor, correlationId);
        log.info("Sucursal cambiada assignmentId={} membershipId={} companyId={} actor={}", assignmentId, membershipId, publicCompanyId, actor);
        return ResponseEntity.ok().header("ETag", "\"" + updated.version() + "\"").body(updated);
    }

    @DeleteMapping("/{assignmentId}")
    @RequiresPermission(value = "assignments.delete", action = "quitar sucursales a una persona")
    public ResponseEntity<Void> remove(
        @PathVariable String publicCompanyId,
        @PathVariable String membershipId,
        @PathVariable String assignmentId,
        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
        Principal principal,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        String actor = actor(principal);
        assignments.remove(publicCompanyId, membershipId, assignmentId, actor, correlationId, idempotencyKey);
        log.info("Sucursal quitada assignmentId={} membershipId={} companyId={} actor={}", assignmentId, membershipId, publicCompanyId, actor);
        return ResponseEntity.noContent().build();
    }

    /** {@code If-Match} trae la versión entre comillas, como el ETag que se devolvió. */
    private static Long versionOf(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(ifMatch.trim().replace("\"", ""));
        } catch (NumberFormatException e) {
            throw new InvalidRequestException("El encabezado If-Match debe traer la versión que consultaste, por ejemplo \"3\".");
        }
    }

    private static String actor(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new UnauthorizedAccessException("Autenticación requerida");
        }
        return principal.getName();
    }
}
