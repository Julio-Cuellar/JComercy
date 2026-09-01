package com.jcode.jcomercy.inventory.infra.web.controller;

import com.jcode.jcomercy.inventory.application.service.StockApplicationService;
import com.jcode.jcomercy.inventory.domain.model.stock.InventoryStatus;
import com.jcode.jcomercy.inventory.infra.security.JwtAuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class StockController {

    private static final Logger log = LoggerFactory.getLogger(StockController.class);
    private final StockApplicationService stockService;
    private final JwtAuthorizationService authorization;

    public StockController(StockApplicationService stockService, JwtAuthorizationService authorization) {
        this.stockService = stockService;
        this.authorization = authorization;
    }

    @PostMapping("/companies/{companyId}/branches/{branchId}/stock/receipts")
    public ResponseEntity<?> receive(@PathVariable String companyId, @PathVariable String branchId,
                                     @Valid @RequestBody ReceiveStockRequest request,
                                     @RequestHeader("Idempotency-Key") String idempotencyKey,
                                     @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
                                     Authentication authentication) {
        log.info("Solicitud recepción de stock companyId={} branchId={} warehouseId={} productId={} actor={}",
                companyId, branchId, request.warehouseId(), request.productId(), actor(authentication));

        if (!authorization.canAccessCompany(authentication, companyId)
                || !authorization.canAccessBranch(authentication, branchId)
                || !authorization.hasPermission(authentication, "inventory.stock.write")) {
            log.warn("Recepción de stock rechazada por permisos companyId={} branchId={} actor={}", companyId, branchId, actor(authentication));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        var result = stockService.receive(new StockApplicationService.ReceiveCommand(
                companyId, branchId, request.warehouseId(), request.locationId(), request.productId(),
                request.quantity(), request.unitCode(), request.unitCost(), request.lotId(),
                request.serialNumber(), request.expirationDate(),
                request.inventoryStatus() != null ? request.inventoryStatus() : InventoryStatus.AVAILABLE,
                request.referenceType(), request.referenceId(), actor(authentication), correlation(correlationId)
        ));

        return ResponseEntity.created(URI.create("/api/v1/companies/" + companyId + "/stock/movements/" + result.movement().publicMovementId()))
                .body(result);
    }

    @PostMapping("/companies/{companyId}/branches/{branchId}/stock/issues")
    public ResponseEntity<?> issue(@PathVariable String companyId, @PathVariable String branchId,
                                   @Valid @RequestBody IssueStockRequest request,
                                   @RequestHeader("Idempotency-Key") String idempotencyKey,
                                   @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
                                   Authentication authentication) {
        log.info("Solicitud salida de stock companyId={} branchId={} warehouseId={} productId={} actor={}",
                companyId, branchId, request.warehouseId(), request.productId(), actor(authentication));

        if (!authorization.canAccessCompany(authentication, companyId)
                || !authorization.canAccessBranch(authentication, branchId)
                || !authorization.hasPermission(authentication, "inventory.stock.write")) {
            log.warn("Salida de stock rechazada por permisos companyId={} branchId={} actor={}", companyId, branchId, actor(authentication));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        var result = stockService.issue(new StockApplicationService.IssueCommand(
                companyId, branchId, request.warehouseId(), request.locationId(), request.productId(),
                request.quantity(), request.unitCode(), request.unitCost(), request.lotId(),
                request.serialNumber(), request.expirationDate(),
                request.inventoryStatus() != null ? request.inventoryStatus() : InventoryStatus.AVAILABLE,
                request.referenceType(), request.referenceId(), actor(authentication), correlation(correlationId)
        ), authorization.hasPermission(authentication, "inventory.stock.negative.enable"));

        return ResponseEntity.created(URI.create("/api/v1/companies/" + companyId + "/stock/movements/" + result.movement().publicMovementId()))
                .body(result);
    }

    @PostMapping("/companies/{companyId}/branches/{branchId}/stock/adjustments")
    public ResponseEntity<?> adjust(@PathVariable String companyId, @PathVariable String branchId,
                                    @Valid @RequestBody AdjustStockRequest request,
                                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                                    @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
                                    Authentication authentication) {
        log.info("Solicitud ajuste de stock direction={} companyId={} branchId={} warehouseId={} productId={} actor={}",
                request.direction(), companyId, branchId, request.warehouseId(), request.productId(), actor(authentication));

        if (!authorization.canAccessCompany(authentication, companyId)
                || !authorization.canAccessBranch(authentication, branchId)
                || !authorization.hasPermission(authentication, "inventory.stock.adjust")) {
            log.warn("Ajuste de stock rechazado por permisos companyId={} branchId={} actor={}", companyId, branchId, actor(authentication));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        var result = stockService.adjust(new StockApplicationService.AdjustmentCommand(
                companyId, branchId, request.warehouseId(), request.locationId(), request.productId(),
                request.quantity(), request.unitCode(), request.unitCost(), request.lotId(),
                request.serialNumber(), request.expirationDate(),
                request.inventoryStatus() != null ? request.inventoryStatus() : InventoryStatus.AVAILABLE,
                request.referenceType(), request.referenceId(), request.reason(), actor(authentication),
                correlation(correlationId), request.direction()
        ), authorization.hasPermission(authentication, "inventory.stock.negative.enable"));

        return ResponseEntity.created(URI.create("/api/v1/companies/" + companyId + "/stock/movements/" + result.movement().publicMovementId()))
                .body(result);
    }

    @GetMapping("/companies/{companyId}/stock/balances")
    public ResponseEntity<?> listBalances(@PathVariable String companyId,
                                           @RequestParam(required = false) UUID productId,
                                           Authentication authentication) {
        if (!authorization.canAccessCompany(authentication, companyId)
                || !authorization.hasPermission(authentication, "inventory.stock.read")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(stockService.listBalances(companyId, productId));
    }

    @GetMapping("/companies/{companyId}/stock/movements")
    public ResponseEntity<?> listMovements(@PathVariable String companyId,
                                            @RequestParam(required = false) UUID productId,
                                            Authentication authentication) {
        if (!authorization.canAccessCompany(authentication, companyId)
                || !authorization.hasPermission(authentication, "inventory.stock.read")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(stockService.listMovements(companyId, productId));
    }

    private static String actor(Authentication authentication) {
        return authentication == null ? "anonymous" : authentication.getName();
    }

    private static String correlation(String correlationId) {
        return (correlationId == null || correlationId.isBlank()) ? "cor_" + UUID.randomUUID().toString().substring(0, 8) : correlationId;
    }

    public record ReceiveStockRequest(
            @NotNull UUID warehouseId,
            @NotNull UUID locationId,
            @NotNull UUID productId,
            @NotNull BigDecimal quantity,
            @NotBlank String unitCode,
            BigDecimal unitCost,
            String lotId,
            String serialNumber,
            LocalDate expirationDate,
            InventoryStatus inventoryStatus,
            String referenceType,
            String referenceId
    ) { }

    public record IssueStockRequest(
            @NotNull UUID warehouseId,
            @NotNull UUID locationId,
            @NotNull UUID productId,
            @NotNull BigDecimal quantity,
            @NotBlank String unitCode,
            BigDecimal unitCost,
            String lotId,
            String serialNumber,
            LocalDate expirationDate,
            InventoryStatus inventoryStatus,
            String referenceType,
            String referenceId
    ) { }

    public record AdjustStockRequest(
            @NotNull UUID warehouseId,
            @NotNull UUID locationId,
            @NotNull UUID productId,
            @NotNull BigDecimal quantity,
            @NotBlank String unitCode,
            BigDecimal unitCost,
            String lotId,
            String serialNumber,
            LocalDate expirationDate,
            InventoryStatus inventoryStatus,
            String referenceType,
            String referenceId,
            @NotBlank String reason,
            @NotNull StockApplicationService.AdjustmentDirection direction
    ) { }
}
