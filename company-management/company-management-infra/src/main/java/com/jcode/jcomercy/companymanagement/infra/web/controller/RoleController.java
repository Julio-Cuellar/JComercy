package com.jcode.jcomercy.companymanagement.infra.web.controller;

import com.jcode.jcomercy.companymanagement.application.dto.PageDto;
import com.jcode.jcomercy.companymanagement.application.dto.RoleViewDto;
import com.jcode.jcomercy.companymanagement.application.service.RoleApplicationService;
import com.jcode.jcomercy.companymanagement.domain.exception.InvalidRequestException;
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
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@RestController
@RequestMapping("/api/v1/companies/{publicCompanyId}/roles")
public class RoleController {
    private static final Logger log = LoggerFactory.getLogger(RoleController.class);

    private final RoleApplicationService roles;
    private final EffectivePermissions effectivePermissions;

    public RoleController(RoleApplicationService roles, EffectivePermissions effectivePermissions) {
        this.roles = roles;
        this.effectivePermissions = effectivePermissions;
    }

    public record PermissionRequest(String permissionCode, List<String> branchIds) { }

    /** El nombre y los permisos de un rol propio. La descripción del contrato todavía no se guarda. */
    public record RoleRequest(String name, List<PermissionRequest> permissions) {
        List<RoleViewDto.Permission> toPermissions() {
            return permissions == null ? null : permissions.stream()
                .map(permission -> permission == null ? null : new RoleViewDto.Permission(permission.permissionCode(), permission.branchIds()))
                .toList();
        }
    }

    @GetMapping
    @RequiresPermission(value = "roles.read", action = "consultar los roles")
    public ResponseEntity<PageDto<RoleViewDto>> list(
        @PathVariable String publicCompanyId,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) String after
    ) {
        return ResponseEntity.ok(roles.list(publicCompanyId, type, status, limit, after));
    }

    @GetMapping("/{roleId}")
    @RequiresPermission(value = "roles.read", action = "consultar los roles")
    public ResponseEntity<RoleViewDto> get(@PathVariable String publicCompanyId, @PathVariable String roleId) {
        RoleViewDto role = roles.get(publicCompanyId, roleId);
        return ResponseEntity.ok().header("ETag", "\"" + role.version() + "\"").body(role);
    }

    @PostMapping
    @RequiresPermission(value = "roles.create", action = "crear roles")
    public ResponseEntity<RoleViewDto> create(
        @PathVariable String publicCompanyId,
        @RequestBody RoleRequest request,
        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
        Principal principal,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        String actor = actor(principal);
        RoleViewDto created = roles.create(publicCompanyId, request.name(), request.toPermissions(),
            canGive(actor, publicCompanyId), actor, correlationId, idempotencyKey);
        log.info("Rol creado roleId={} companyId={} actor={} correlationId={}", created.roleId(), publicCompanyId, actor, correlationId);
        return ResponseEntity.created(URI.create("/api/v1/companies/" + publicCompanyId + "/roles/" + created.roleId()))
            .header("ETag", "\"" + created.version() + "\"").body(created);
    }

    @PatchMapping("/{roleId}")
    @RequiresPermission(value = "roles.update", action = "editar roles")
    public ResponseEntity<RoleViewDto> update(
        @PathVariable String publicCompanyId,
        @PathVariable String roleId,
        @RequestBody RoleRequest request,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        Principal principal,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        String actor = actor(principal);
        RoleViewDto updated = roles.update(publicCompanyId, roleId, request.name(), request.toPermissions(),
            versionOf(ifMatch), canGive(actor, publicCompanyId), actor, correlationId);
        log.info("Rol editado roleId={} companyId={} actor={} correlationId={}", roleId, publicCompanyId, actor, correlationId);
        return ResponseEntity.ok().header("ETag", "\"" + updated.version() + "\"").body(updated);
    }

    @PostMapping("/{roleId}/deactivation")
    @RequiresPermission(value = "roles.deactivate", action = "desactivar roles")
    public ResponseEntity<RoleViewDto> deactivate(
        @PathVariable String publicCompanyId,
        @PathVariable String roleId,
        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
        Principal principal,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        String actor = actor(principal);
        RoleViewDto deactivated = roles.deactivate(publicCompanyId, roleId, actor, correlationId, idempotencyKey);
        log.info("Rol desactivado roleId={} companyId={} actor={} correlationId={}", roleId, publicCompanyId, actor, correlationId);
        return ResponseEntity.ok().header("ETag", "\"" + deactivated.version() + "\"").body(deactivated);
    }

    @DeleteMapping("/{roleId}")
    @RequiresPermission(value = "roles.delete", action = "eliminar roles")
    public ResponseEntity<Void> delete(
        @PathVariable String publicCompanyId,
        @PathVariable String roleId,
        @RequestParam(required = false) Boolean confirmation,
        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
        Principal principal,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        String actor = actor(principal);
        roles.delete(publicCompanyId, roleId, confirmation, actor, correlationId, idempotencyKey);
        log.info("Rol eliminado roleId={} companyId={} actor={} correlationId={}", roleId, publicCompanyId, actor, correlationId);
        return ResponseEntity.noContent().build();
    }

    /** Un permiso solo lo da quien lo tiene: se evalúa con lo que la persona puede hacer hoy en esta empresa. */
    private Predicate<String> canGive(String actor, String companyId) {
        Set<String> granted = effectivePermissions.inCompany(actor, companyId);
        return code -> Permissions.grants(granted, code);
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
