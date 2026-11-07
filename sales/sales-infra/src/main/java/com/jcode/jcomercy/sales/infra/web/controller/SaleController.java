package com.jcode.jcomercy.sales.infra.web.controller;

import com.jcode.jcomercy.sales.application.port.InventoryGatewayPort;
import com.jcode.jcomercy.sales.application.service.SaleCancellationService;
import com.jcode.jcomercy.sales.application.service.SaleCheckoutService;
import com.jcode.jcomercy.sales.application.service.SaleQueryService;
import com.jcode.jcomercy.sales.domain.model.sale.Payment;
import com.jcode.jcomercy.sales.domain.model.sale.PaymentMethod;
import com.jcode.jcomercy.sales.infra.security.JwtAuthorizationService;
import com.jcode.jcomercy.sales.infra.web.dto.SaleResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
import java.util.List;
import java.util.UUID;

/** Las ventas de una sucursal: se consultan con {@code sales.read}, solo en las sucursales que nombra el token. */
@RestController
@RequestMapping("/api/v1")
public class SaleController {

    private static final Logger log = LoggerFactory.getLogger(SaleController.class);

    private final SaleQueryService queries;
    private final SaleCheckoutService checkout;
    private final SaleCancellationService cancellation;
    private final JwtAuthorizationService authorization;

    public SaleController(SaleQueryService queries, SaleCheckoutService checkout, SaleCancellationService cancellation,
                          JwtAuthorizationService authorization) {
        this.queries = queries;
        this.checkout = checkout;
        this.cancellation = cancellation;
        this.authorization = authorization;
    }

    /**
     * Cobra una venta de mostrador. 201 si se cobró ahora; 200 si la misma llave ya había cobrado esa venta. El token de
     * quien vende se reenvía a inventario, que decide con sus propios permisos.
     */
    @PostMapping("/companies/{companyId}/branches/{branchId}/sales")
    public ResponseEntity<SaleResponse> create(@PathVariable String companyId, @PathVariable String branchId,
                                               @Valid @RequestBody CreateSaleRequest request,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               @RequestHeader("Authorization") String authorizationHeader,
                                               @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
                                               Authentication authentication) {
        if (!authorization.canAccessCompany(authentication, companyId)
                || !authorization.canAccessBranch(authentication, branchId)
                || !authorization.hasPermission(authentication, "sales.create")) {
            log.warn("Venta rechazada por permisos companyId={} branchId={} actor={}", companyId, branchId, authentication.getName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var result = checkout.checkout(new SaleCheckoutService.CheckoutCommand(companyId, branchId, request.warehouseId(),
                request.lines().stream().map(line -> new SaleCheckoutService.LineInput(line.productId(), line.quantity())).toList(),
                request.payments().stream().map(payment -> new Payment(payment.method(), payment.amount(), payment.reference())).toList(),
                authentication.getName(), idempotencyKey,
                new InventoryGatewayPort.Caller(authorizationHeader, correlation(correlationId))));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(SaleResponse.from(result.sale()));
    }

    /**
     * Cancela la venta completa y regresa su stock a inventario. Es segura de repetir sin llave de idempotencia: el estado
     * de la venta y las llaves que salen de ella evitan devolver dos veces. El token de quien cancela se reenvía a
     * inventario, que pide {@code inventory.stock.write} para devolver stock.
     */
    @PostMapping("/companies/{companyId}/branches/{branchId}/sales/{saleId}/cancellation")
    public ResponseEntity<SaleResponse> cancel(@PathVariable String companyId, @PathVariable String branchId,
                                               @PathVariable UUID saleId, @Valid @RequestBody CancelSaleRequest request,
                                               @RequestHeader("Authorization") String authorizationHeader,
                                               @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
                                               Authentication authentication) {
        if (!authorization.canAccessCompany(authentication, companyId)
                || !authorization.canAccessBranch(authentication, branchId)
                || !authorization.hasPermission(authentication, "sales.cancel")) {
            log.warn("Cancelación rechazada por permisos companyId={} branchId={} actor={}", companyId, branchId, authentication.getName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var cancelled = cancellation.cancel(new SaleCancellationService.CancelCommand(companyId, branchId, saleId, request.reason(),
                authentication.getName(), new InventoryGatewayPort.Caller(authorizationHeader, correlation(correlationId))));
        return ResponseEntity.ok(SaleResponse.from(cancelled));
    }

    @GetMapping("/companies/{companyId}/branches/{branchId}/sales")
    public ResponseEntity<List<SaleResponse>> list(@PathVariable String companyId, @PathVariable String branchId,
                                                   Authentication authentication) {
        if (!canRead(authentication, companyId, branchId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(queries.list(companyId, branchId).stream().map(SaleResponse::from).toList());
    }

    @GetMapping("/companies/{companyId}/branches/{branchId}/sales/{saleId}")
    public ResponseEntity<SaleResponse> get(@PathVariable String companyId, @PathVariable String branchId,
                                            @PathVariable UUID saleId, Authentication authentication) {
        if (!canRead(authentication, companyId, branchId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(SaleResponse.from(queries.get(companyId, branchId, saleId)));
    }

    private boolean canRead(Authentication authentication, String companyId, String branchId) {
        return authorization.canAccessCompany(authentication, companyId)
                && authorization.canAccessBranch(authentication, branchId)
                && authorization.hasPermission(authentication, "sales.read");
    }

    private static String correlation(String correlationId) {
        return (correlationId == null || correlationId.isBlank())
                ? "cor_" + UUID.randomUUID().toString().substring(0, 8) : correlationId;
    }

    public record CancelSaleRequest(
            @NotBlank(message = "es obligatorio") @Size(max = 500, message = "no puede pasar de 500 caracteres") String reason) { }

    public record CreateSaleRequest(
            @NotNull(message = "es obligatorio") UUID warehouseId,
            @NotEmpty(message = "necesita al menos un producto") @Valid List<LineRequest> lines,
            @NotEmpty(message = "necesita al menos un pago") @Valid List<PaymentRequest> payments) { }

    public record LineRequest(
            @NotNull(message = "es obligatorio") UUID productId,
            @NotNull(message = "es obligatoria") @DecimalMin(value = "0", inclusive = false, message = "debe ser mayor que cero")
            BigDecimal quantity) { }

    public record PaymentRequest(
            @NotNull(message = "es obligatorio") PaymentMethod method,
            @NotNull(message = "es obligatorio") @DecimalMin(value = "0", inclusive = false, message = "debe ser mayor que cero")
            BigDecimal amount,
            @Size(max = 64, message = "no puede pasar de 64 caracteres") String reference) { }
}
