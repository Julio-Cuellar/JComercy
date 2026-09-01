package com.jcode.jcomercy.inventory.infra.web.controller;

import com.jcode.jcomercy.inventory.application.service.ProductApplicationService;
import com.jcode.jcomercy.inventory.domain.model.product.ProductTrackingMode;
import com.jcode.jcomercy.inventory.domain.model.unit.UnitDimension;
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
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;
import java.math.RoundingMode;

@RestController
@RequestMapping("/api/v1")
public class ProductController {
    private static final Logger log = LoggerFactory.getLogger(ProductController.class);
    private final ProductApplicationService service;
    private final JwtAuthorizationService authorization;

    public ProductController(ProductApplicationService service, JwtAuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @PostMapping("/companies/{companyId}/products")
    public ResponseEntity<?> create(@PathVariable String companyId, @Valid @RequestBody CreateProductRequest request,
                                    @RequestHeader("Idempotency-Key") String idempotencyKey, Authentication authentication) {
        log.info("Solicitud crear producto companyId={} sku={} actor={}", companyId, request.sku(), actor(authentication));
        if (!authorization.canAccessCompany(authentication, companyId)
                || !authorization.hasPermission(authentication, "inventory.products.manage")) {
            log.warn("Crear producto rechazado companyId={} actor={}", companyId, actor(authentication));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var product = service.create(new ProductApplicationService.CreateProductCommand(companyId, request.sku(), request.name(),
                request.categoryId(), request.stockUnitCode(), request.purchaseUnitCode(), request.saleUnitCode(),
                request.netQuantity(), request.netQuantityUnitCode(), request.trackingMode(), request.allowNegativeStock()));
        return ResponseEntity.created(URI.create("/api/v1/products/" + product.id())).body(product);
    }

    @GetMapping("/companies/{companyId}/products")
    public ResponseEntity<?> list(@PathVariable String companyId, Authentication authentication) {
        if (!authorization.canAccessCompany(authentication, companyId)
                || !authorization.hasAnyPermission(authentication, "inventory.products.read", "inventory.products.manage")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(service.listByCompany(companyId));
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<?> get(@PathVariable UUID productId, Authentication authentication) {
        var product = service.get(productId);
        if (!authorization.canAccessCompany(authentication, product.companyId())
                || !authorization.hasAnyPermission(authentication, "inventory.products.read", "inventory.products.manage")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(product);
    }

    @PostMapping("/companies/{companyId}/products/{productId}/unit-conversions")
    public ResponseEntity<?> addConversion(@PathVariable String companyId, @PathVariable UUID productId,
                                           @Valid @RequestBody AddConversionRequest request,
                                           @RequestHeader("Idempotency-Key") String idempotencyKey,
                                           Authentication authentication) {
        if (!authorization.canAccessCompany(authentication, companyId)
                || !authorization.hasPermission(authentication, "inventory.products.manage")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var conversion = service.addConversion(companyId, productId, new ProductApplicationService.AddConversionCommand(
                request.fromUnitCode(), request.toUnitCode(), request.factor(), request.roundingScale(), request.roundingMode(),
                request.purchaseUnit(), request.salesUnit(), request.defaultConversion()));
        return ResponseEntity.created(URI.create("/api/v1/products/" + productId + "/unit-conversions/" + conversion.id())).body(conversion);
    }

    @GetMapping("/companies/{companyId}/products/{productId}/unit-conversions")
    public ResponseEntity<?> listConversions(@PathVariable String companyId, @PathVariable UUID productId,
                                             Authentication authentication) {
        if (!authorization.canAccessCompany(authentication, companyId)
                || !authorization.hasAnyPermission(authentication, "inventory.products.read", "inventory.products.manage")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(service.listConversions(companyId, productId));
    }

    @GetMapping("/units-of-measure")
    public ResponseEntity<?> units() { return ResponseEntity.ok(service.listUnits()); }

    @GetMapping("/companies/{companyId}/units-of-measure")
    public ResponseEntity<?> companyUnits(@PathVariable String companyId, Authentication authentication) {
        if (!authorization.canAccessCompany(authentication, companyId)
                || !authorization.hasAnyPermission(authentication, "inventory.units.read", "inventory.units.manage")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(service.listUnits(companyId));
    }

    @PostMapping("/companies/{companyId}/units-of-measure")
    public ResponseEntity<?> createCompanyUnit(@PathVariable String companyId, @Valid @RequestBody CreateCompanyUnitRequest request,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               Authentication authentication) {
        if (!authorization.canAccessCompany(authentication, companyId)
                || !authorization.hasPermission(authentication, "inventory.units.manage")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var unit = service.createCompanyUnit(new ProductApplicationService.CreateCompanyUnitCommand(companyId, request.code(),
                request.name(), request.symbol(), request.dimension(), request.baseUnitCode(), request.conversionFactor(), request.decimals()));
        return ResponseEntity.created(URI.create("/api/v1/companies/" + companyId + "/units-of-measure/" + unit.publicUnitId())).body(unit);
    }

    private static String actor(Authentication authentication) { return authentication == null ? "anonymous" : authentication.getName(); }

    public record CreateProductRequest(@NotBlank String sku, @NotBlank String name, String categoryId,
                                       @NotBlank String stockUnitCode, String purchaseUnitCode, String saleUnitCode,
                                       BigDecimal netQuantity, String netQuantityUnitCode,
                                       @NotNull ProductTrackingMode trackingMode, boolean allowNegativeStock) { }
    public record AddConversionRequest(@NotBlank String fromUnitCode, @NotBlank String toUnitCode,
                                       @NotNull BigDecimal factor, int roundingScale,
                                       @NotNull RoundingMode roundingMode, boolean purchaseUnit, boolean salesUnit,
                                       boolean defaultConversion) { }
    public record CreateCompanyUnitRequest(@NotBlank String code, @NotBlank String name, @NotBlank String symbol,
                                           @NotNull UnitDimension dimension, @NotBlank String baseUnitCode,
                                           @NotNull BigDecimal conversionFactor, int decimals) { }
}
