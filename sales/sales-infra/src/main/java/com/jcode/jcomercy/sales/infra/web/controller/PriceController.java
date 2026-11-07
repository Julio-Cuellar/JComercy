package com.jcode.jcomercy.sales.infra.web.controller;

import com.jcode.jcomercy.sales.application.service.PriceApplicationService;
import com.jcode.jcomercy.sales.infra.security.JwtAuthorizationService;
import com.jcode.jcomercy.sales.infra.web.dto.ProductPriceResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** El precio de venta de cada producto: se fija con {@code sales.prices.write} y se consulta con {@code sales.read}. */
@RestController
@RequestMapping("/api/v1")
public class PriceController {
    private static final Logger log = LoggerFactory.getLogger(PriceController.class);

    private final PriceApplicationService service;
    private final JwtAuthorizationService authorization;

    public PriceController(PriceApplicationService service, JwtAuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @PutMapping("/companies/{companyId}/products/{productId}/price")
    public ResponseEntity<ProductPriceResponse> setPrice(@PathVariable String companyId, @PathVariable UUID productId,
                                                         @Valid @RequestBody SetPriceRequest request,
                                                         Authentication authentication) {
        if (!authorization.canAccessCompany(authentication, companyId)
                || !authorization.hasPermission(authentication, "sales.prices.write")) {
            log.warn("Precio rechazado por permisos companyId={} actor={}", companyId, authentication == null ? null : authentication.getName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var price = service.setPrice(new PriceApplicationService.SetPriceCommand(companyId, productId, request.amount(),
                request.currency(), authentication.getName()));
        return ResponseEntity.ok(ProductPriceResponse.from(price));
    }

    @GetMapping("/companies/{companyId}/prices")
    public ResponseEntity<List<ProductPriceResponse>> listPrices(@PathVariable String companyId, Authentication authentication) {
        if (!authorization.canAccessCompany(authentication, companyId) || !authorization.hasPermission(authentication, "sales.read")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(service.list(companyId).stream().map(ProductPriceResponse::from).toList());
    }

    public record SetPriceRequest(
            @NotNull(message = "es obligatorio") @DecimalMin(value = "0.00", message = "no puede ser negativo") BigDecimal amount,
            @NotNull(message = "es obligatoria") @Pattern(regexp = "[A-Za-z]{3}",
                    message = "debe ser un código de tres letras, por ejemplo MXN") String currency) { }
}
