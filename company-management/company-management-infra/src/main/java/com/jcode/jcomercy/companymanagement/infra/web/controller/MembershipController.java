package com.jcode.jcomercy.companymanagement.infra.web.controller;

import com.jcode.jcomercy.companymanagement.application.dto.EffectivePermissionsDto;
import com.jcode.jcomercy.companymanagement.application.dto.LifecycleDto;
import com.jcode.jcomercy.companymanagement.application.service.MembershipLifecycleService;
import com.jcode.jcomercy.companymanagement.application.dto.MembershipDetailDto;
import com.jcode.jcomercy.companymanagement.application.dto.MembershipSummaryDto;
import com.jcode.jcomercy.companymanagement.application.dto.PageDto;
import com.jcode.jcomercy.companymanagement.application.service.MembershipApplicationService;
import com.jcode.jcomercy.companymanagement.domain.exception.UnauthorizedAccessException;
import com.jcode.jcomercy.companymanagement.infra.security.EffectivePermissions;
import com.jcode.jcomercy.companymanagement.infra.security.RequiresPermission;
import com.jcode.jcomercy.core.authorization.Permissions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@RestController
@RequestMapping("/api/v1/companies/{publicCompanyId}/memberships")
public class MembershipController {

    private final MembershipApplicationService memberships;
    private final MembershipLifecycleService lifecycle;
    private final EffectivePermissions effectivePermissions;

    public MembershipController(MembershipApplicationService memberships, MembershipLifecycleService lifecycle,
                                EffectivePermissions effectivePermissions) {
        this.memberships = memberships;
        this.lifecycle = lifecycle;
        this.effectivePermissions = effectivePermissions;
    }

    /** Suspende el acceso de la persona; se puede reactivar. Corta el acceso en la siguiente petición. */
    @PostMapping("/{membershipId}/suspension")
    @RequiresPermission(value = "memberships.suspend", action = "suspender el acceso de una persona")
    public ResponseEntity<LifecycleDto> suspend(
        @PathVariable String publicCompanyId,
        @PathVariable String membershipId,
        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
        Principal principal,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        return ResponseEntity.ok(lifecycle.suspend(publicCompanyId, membershipId, actor(principal), correlationId, idempotencyKey));
    }

    /** Devuelve el acceso a una persona suspendida. */
    @PostMapping("/{membershipId}/reactivation")
    @RequiresPermission(value = "memberships.reactivate", action = "reactivar el acceso de una persona")
    public ResponseEntity<LifecycleDto> reactivate(
        @PathVariable String publicCompanyId,
        @PathVariable String membershipId,
        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
        Principal principal,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        return ResponseEntity.ok(lifecycle.reactivate(publicCompanyId, membershipId, actor(principal), correlationId, idempotencyKey));
    }

    /** Revoca el acceso de la persona, de forma definitiva. */
    @PostMapping("/{membershipId}/revocation")
    @RequiresPermission(value = "memberships.revoke", action = "revocar el acceso de una persona")
    public ResponseEntity<LifecycleDto> revoke(
        @PathVariable String publicCompanyId,
        @PathVariable String membershipId,
        @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
        Principal principal,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        return ResponseEntity.ok(lifecycle.revoke(publicCompanyId, membershipId, actor(principal), correlationId, idempotencyKey));
    }

    @GetMapping
    @RequiresPermission(value = "memberships.read", action = "consultar al equipo de la empresa")
    public ResponseEntity<PageDto<MembershipSummaryDto>> list(
        @PathVariable String publicCompanyId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String branchId,
        @RequestParam(required = false) String roleId,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) String after
    ) {
        return ResponseEntity.ok(memberships.list(publicCompanyId, status, branchId, roleId, limit, after));
    }

    @GetMapping("/{membershipId}")
    @RequiresPermission(value = "memberships.read", action = "consultar al equipo de la empresa")
    public ResponseEntity<MembershipDetailDto> get(@PathVariable String publicCompanyId, @PathVariable String membershipId) {
        MembershipDetailDto membership = memberships.get(publicCompanyId, membershipId);
        return ResponseEntity.ok().header("ETag", "\"" + membership.version() + "\"").body(membership);
    }

    /**
     * Lo que la persona que consulta puede hacer hoy en esta empresa. Sale del mismo cálculo que decide cada petición.
     * Para quien es dueña de la cuenta es todo ({@code *}) en toda la empresa; para el resto, los permisos de sus
     * roles y las sucursales que tiene asignadas y activas.
     */
    @GetMapping("/me/effective-permissions")
    @RequiresPermission(value = "effective_permissions.read_own", action = "consultar tus permisos")
    public ResponseEntity<EffectivePermissionsDto> myEffectivePermissions(@PathVariable String publicCompanyId, Principal principal) {
        String actor = actor(principal);
        Set<String> granted = new TreeSet<>(effectivePermissions.inCompany(actor, publicCompanyId));
        List<String> branches = granted.contains(Permissions.ALL) ? List.of() : memberships.activeBranchIds(publicCompanyId, actor);
        List<EffectivePermissionsDto.Permission> permissions = granted.stream()
            .map(code -> new EffectivePermissionsDto.Permission(code, branches)).toList();
        return ResponseEntity.ok(new EffectivePermissionsDto(actor, publicCompanyId, permissions, Instant.now()));
    }

    private static String actor(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new UnauthorizedAccessException("Autenticación requerida");
        }
        return principal.getName();
    }
}
