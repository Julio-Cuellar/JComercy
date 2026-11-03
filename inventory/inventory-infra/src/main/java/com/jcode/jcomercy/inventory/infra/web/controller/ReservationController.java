package com.jcode.jcomercy.inventory.infra.web.controller;

import com.jcode.jcomercy.inventory.application.service.ReservationApplicationService;
import com.jcode.jcomercy.inventory.infra.security.JwtAuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ReservationController {
    private static final Logger log = LoggerFactory.getLogger(ReservationController.class);

    private final ReservationApplicationService service;
    private final JwtAuthorizationService authorization;

    public ReservationController(ReservationApplicationService service, JwtAuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @PostMapping("/companies/{companyId}/branches/{branchId}/stock/reservations")
    public ResponseEntity<?> reserve(@PathVariable String companyId, @PathVariable String branchId,
                                     @Valid @RequestBody ReserveRequest request,
                                     @RequestHeader("Idempotency-Key") String idempotencyKey,
                                     @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
                                     Authentication authentication) {
        log.info("Solicitud reservar stock companyId={} branchId={} warehouseId={} productId={} referencia={}/{} actor={}",
                companyId, branchId, request.warehouseId(), request.productId(), request.referenceType(),
                request.referenceId(), actor(authentication));
        if (!canWrite(authentication, companyId, branchId)) {
            log.warn("Reserva rechazada por permisos companyId={} branchId={} actor={}", companyId, branchId, actor(authentication));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var result = service.reserve(new ReservationApplicationService.ReserveCommand(companyId, branchId,
                request.warehouseId(), request.locationId(), request.productId(), request.quantity(), request.unitCode(),
                request.referenceType(), request.referenceId(), request.expiresAt(), actor(authentication),
                correlation(correlationId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/companies/{companyId}/branches/{branchId}/stock/reservations/release")
    public ResponseEntity<?> release(@PathVariable String companyId, @PathVariable String branchId,
                                     @Valid @RequestBody ReferenceRequest request,
                                     @RequestHeader("Idempotency-Key") String idempotencyKey,
                                     @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
                                     Authentication authentication) {
        log.info("Solicitud liberar reserva companyId={} branchId={} referencia={}/{} actor={}", companyId, branchId,
                request.referenceType(), request.referenceId(), actor(authentication));
        if (!canWrite(authentication, companyId, branchId)) {
            log.warn("Liberación rechazada por permisos companyId={} branchId={} actor={}", companyId, branchId, actor(authentication));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(service.release(command(companyId, branchId, request, authentication, correlationId)));
    }

    @PostMapping("/companies/{companyId}/branches/{branchId}/stock/reservations/commit")
    public ResponseEntity<?> commit(@PathVariable String companyId, @PathVariable String branchId,
                                    @Valid @RequestBody ReferenceRequest request,
                                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                                    @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
                                    Authentication authentication) {
        log.info("Solicitud confirmar reserva companyId={} branchId={} referencia={}/{} actor={}", companyId, branchId,
                request.referenceType(), request.referenceId(), actor(authentication));
        if (!canWrite(authentication, companyId, branchId)) {
            log.warn("Confirmación rechazada por permisos companyId={} branchId={} actor={}", companyId, branchId, actor(authentication));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(service.commit(command(companyId, branchId, request, authentication, correlationId)));
    }

    @GetMapping("/companies/{companyId}/stock/availability")
    public ResponseEntity<?> availability(@PathVariable String companyId,
                                          @RequestParam(required = false) UUID productId,
                                          Authentication authentication) {
        if (!authorization.canAccessCompany(authentication, companyId)
                || !authorization.hasPermission(authentication, "inventory.stock.read")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(service.availability(companyId, productId));
    }

    private boolean canWrite(Authentication authentication, String companyId, String branchId) {
        return authorization.canAccessCompany(authentication, companyId)
                && authorization.canAccessBranch(authentication, branchId)
                && authorization.hasPermission(authentication, "inventory.stock.write");
    }

    private static ReservationApplicationService.ReferenceCommand command(String companyId, String branchId,
                                                                          ReferenceRequest request,
                                                                          Authentication authentication,
                                                                          String correlationId) {
        return new ReservationApplicationService.ReferenceCommand(companyId, branchId, request.referenceType(),
                request.referenceId(), request.reason(), actor(authentication), correlation(correlationId));
    }

    private static String actor(Authentication authentication) {
        return authentication == null ? "anonymous" : authentication.getName();
    }

    private static String correlation(String correlationId) {
        return (correlationId == null || correlationId.isBlank()) ? "cor_" + UUID.randomUUID().toString().substring(0, 8) : correlationId;
    }

    public record ReserveRequest(
            @NotNull UUID warehouseId,
            UUID locationId,
            @NotNull UUID productId,
            @NotNull BigDecimal quantity,
            @NotBlank String unitCode,
            @NotBlank String referenceType,
            @NotBlank String referenceId,
            Instant expiresAt) {
    }

    public record ReferenceRequest(
            @NotBlank String referenceType,
            @NotBlank String referenceId,
            String reason) {
    }
}
