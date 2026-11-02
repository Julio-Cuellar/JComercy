package com.jcode.jcomercy.inventory.infra.web.controller;

import com.jcode.jcomercy.inventory.application.service.WarehouseApplicationService;
import com.jcode.jcomercy.inventory.domain.model.warehouse.LocationType;
import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseScope;
import com.jcode.jcomercy.inventory.domain.model.warehouse.WarehouseType;
import com.jcode.jcomercy.inventory.infra.security.JwtAuthorizationService;
import com.jcode.jcomercy.inventory.domain.port.WarehouseRepositoryPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.UUID;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/v1")
public class WarehouseController {
    private static final Logger log = LoggerFactory.getLogger(WarehouseController.class);

    private final WarehouseApplicationService service;
    private final JwtAuthorizationService authorization;
    private final WarehouseRepositoryPort warehouses;

    public WarehouseController(WarehouseApplicationService service, JwtAuthorizationService authorization,
                               WarehouseRepositoryPort warehouses) {
        this.service = service;
        this.authorization = authorization;
        this.warehouses = warehouses;
    }

    @PostMapping("/companies/{companyId}/warehouses")
    public ResponseEntity<?> createWarehouse(@PathVariable String companyId,
                                              @Valid
                                              @RequestBody CreateWarehouseRequest request,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                                              Authentication authentication) {
        log.info("Solicitud crear almacén companyId={} actor={}", companyId,
                authentication == null ? "anonymous" : authentication.getName());
        if (!authorization.canAccessCompany(authentication, companyId)
                || !authorization.hasPermission(authentication, "inventory.warehouses.manage")) {
            log.warn("Crear almacén rechazado companyId={} actor={}", companyId,
                    authentication == null ? "anonymous" : authentication.getName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var warehouse = service.createWarehouse(new WarehouseApplicationService.CreateWarehouseCommand(
                companyId, request.scope(), request.branchId(), request.code(), request.name(), request.type(),
                request.defaultWarehouse(), request.address(), request.phone(), request.email(), request.managerId(),
                request.timezone(), request.currency(), actor(authentication)));
        log.info("Almacén creado warehouseId={} companyId={}", warehouse.publicWarehouseId(), companyId);
        return ResponseEntity.created(URI.create("/api/v1/warehouses/" + warehouse.publicWarehouseId())).body(warehouse);
    }

    @PostMapping("/companies/{companyId}/branches/{branchId}/warehouses")
    public ResponseEntity<?> createBranchWarehouse(@PathVariable String companyId, @PathVariable String branchId,
                                                    @Valid @RequestBody CreateBranchWarehouseRequest request,
                                                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                    Authentication authentication) {
        log.info("Solicitud crear almacén de sucursal companyId={} branchId={} actor={}", companyId, branchId,
                authentication == null ? "anonymous" : authentication.getName());
        if (!authorization.canAccessCompany(authentication, companyId)
                || !authorization.canAccessBranch(authentication, branchId)
                || !authorization.hasPermission(authentication, "inventory.warehouses.manage")) {
            log.warn("Crear almacén de sucursal rechazado companyId={} branchId={} actor={}", companyId, branchId,
                    authentication == null ? "anonymous" : authentication.getName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var warehouse = service.createBranchWarehouse(companyId, branchId,
                new WarehouseApplicationService.CreateBranchWarehouseCommand(request.code(), request.name(),
                        request.type(), request.defaultWarehouse(), request.address(), request.phone(), request.email(),
                        request.managerId(), request.timezone(), request.currency(), actor(authentication)));
        log.info("Almacén de sucursal creado warehouseId={} companyId={} branchId={}", warehouse.publicWarehouseId(),
                companyId, branchId);
        return ResponseEntity.created(URI.create("/api/v1/warehouses/" + warehouse.publicWarehouseId())).body(warehouse);
    }

    @GetMapping("/companies/{companyId}/warehouses")
    public ResponseEntity<?> listByCompany(@PathVariable String companyId, Authentication authentication) {
        log.info("Consulta almacenes por empresa companyId={} actor={}", companyId,
                authentication == null ? "anonymous" : authentication.getName());
        if (!authorization.canAccessCompany(authentication, companyId)
                || !authorization.hasAnyPermission(authentication, "inventory.warehouses.read", "inventory.warehouses.manage")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(service.listByCompany(companyId));
    }

    @GetMapping("/branches/{branchId}/warehouses")
    public ResponseEntity<?> listByBranch(@PathVariable String branchId, Authentication authentication) {
        log.info("Consulta almacenes por sucursal branchId={} actor={}", branchId,
                authentication == null ? "anonymous" : authentication.getName());
        if (!authorization.canAccessBranch(authentication, branchId)
                || !authorization.hasAnyPermission(authentication, "inventory.warehouses.read", "inventory.warehouses.manage")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(service.listByBranch(branchId));
    }

    @GetMapping("/warehouses/{warehouseId}")
    public ResponseEntity<?> getWarehouse(@PathVariable UUID warehouseId, Authentication authentication) {
        log.info("Consulta almacén warehouseId={} actor={}", warehouseId,
                authentication == null ? "anonymous" : authentication.getName());
        var warehouse = warehouses.findById(warehouseId).orElse(null);
        if (warehouse == null) {
            return ResponseEntity.notFound().build();
        }
        if (!authorization.canAccessWarehouse(authentication, warehouse)
                || !authorization.hasAnyPermission(authentication, "inventory.warehouses.read", "inventory.warehouses.manage")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(warehouse);
    }

    @PostMapping("/warehouses/{warehouseId}/activate")
    public ResponseEntity<?> activateWarehouse(@PathVariable UUID warehouseId,
                                                @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                Authentication authentication) {
        var warehouse = warehouses.findById(warehouseId).orElse(null);
        if (!authorization.canAccessWarehouse(authentication, warehouse)
                || !authorization.hasPermission(authentication, "inventory.warehouses.manage")) {
            log.warn("Activar almacén rechazado warehouseId={} actor={}", warehouseId, actor(authentication));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(service.activate(warehouseId, actor(authentication)));
    }

    @PostMapping("/warehouses/{warehouseId}/branches")
    public ResponseEntity<?> grantBranchAccess(@PathVariable UUID warehouseId,
                                                @Valid @RequestBody GrantBranchAccessRequest request,
                                                @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                Authentication authentication) {
        log.info("Solicitud conceder acceso warehouseId={} branchId={} actor={}", warehouseId, request.branchId(),
                authentication == null ? "anonymous" : authentication.getName());
        var warehouse = warehouses.findById(warehouseId).orElse(null);
        if (!authorization.canAccessWarehouse(authentication, warehouse)
                || !authorization.canAccessBranch(authentication, request.branchId())
                || !authorization.hasPermission(authentication, "inventory.warehouses.manage")) {
            log.warn("Conceder acceso rechazado warehouseId={} branchId={} actor={}", warehouseId, request.branchId(),
                    authentication == null ? "anonymous" : authentication.getName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var access = service.grantBranchAccess(new WarehouseApplicationService.GrantBranchAccessCommand(
                warehouseId, request.branchId(), request.assignedBy()));
        log.info("Acceso de sucursal otorgado warehouseId={} branchId={}", warehouseId, request.branchId());
        return ResponseEntity.created(URI.create("/api/v1/warehouses/" + warehouseId + "/branches/" + access.branchId()))
                .body(access);
    }

    @PostMapping("/warehouses/{warehouseId}/locations")
    public ResponseEntity<?> createLocation(@PathVariable UUID warehouseId,
                                             @Valid @RequestBody CreateLocationRequest request,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey,
                                             Authentication authentication) {
        log.info("Solicitud crear ubicación warehouseId={} code={} actor={}", warehouseId, request.code(),
                authentication == null ? "anonymous" : authentication.getName());
        var warehouse = warehouses.findById(warehouseId).orElse(null);
        if (!authorization.canAccessWarehouse(authentication, warehouse)
                || !authorization.hasPermission(authentication, "inventory.warehouses.manage")) {
            log.warn("Crear ubicación rechazado warehouseId={} actor={}", warehouseId,
                    authentication == null ? "anonymous" : authentication.getName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var location = service.createLocation(new WarehouseApplicationService.CreateLocationCommand(
                warehouseId, request.parentLocationId(), request.code(), request.name(), request.type(),
                request.capacity(), request.capacityUnitCode()));
        log.info("Ubicación creada locationId={} warehouseId={}", location.publicLocationId(), warehouseId);
        return ResponseEntity.created(URI.create("/api/v1/locations/" + location.publicLocationId())).body(location);
    }

    public record CreateWarehouseRequest(
            @NotNull WarehouseScope scope,
            String branchId,
            @NotBlank String code,
            @NotBlank String name,
            @NotNull WarehouseType type,
            boolean defaultWarehouse,
            String address,
            String phone,
            String email,
            String managerId,
            @NotBlank String timezone,
            @NotBlank String currency) {
    }

    public record CreateBranchWarehouseRequest(
            @NotBlank String code,
            @NotBlank String name,
            @NotNull WarehouseType type,
            boolean defaultWarehouse,
            String address,
            String phone,
            String email,
            String managerId,
            @NotBlank String timezone,
            @NotBlank String currency) {
    }

    public record GrantBranchAccessRequest(@NotBlank String branchId, @NotBlank String assignedBy) {
    }

    public record CreateLocationRequest(
            UUID parentLocationId,
            @NotBlank String code,
            String name,
            @NotNull LocationType type,
            java.math.BigDecimal capacity,
            String capacityUnitCode) {
    }

    private static String actor(Authentication authentication) {
        return authentication == null ? "anonymous" : authentication.getName();
    }
}
