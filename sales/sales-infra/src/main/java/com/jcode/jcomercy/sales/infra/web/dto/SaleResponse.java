package com.jcode.jcomercy.sales.infra.web.dto;

import com.jcode.jcomercy.sales.domain.model.sale.Payment;
import com.jcode.jcomercy.sales.domain.model.sale.Sale;
import com.jcode.jcomercy.sales.domain.model.sale.SaleLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Una venta como la describe el contrato REST. */
public record SaleResponse(
        UUID saleId,
        String folio,
        String channel,
        String externalReference,
        String status,
        String branchId,
        UUID warehouseId,
        String currency,
        BigDecimal total,
        BigDecimal received,
        BigDecimal change,
        List<LineResponse> lines,
        List<PaymentResponse> payments,
        String actorUserId,
        Instant createdAt,
        Instant completedAt,
        Instant cancelledAt,
        String cancelReason
) {

    public static SaleResponse from(Sale sale) {
        return new SaleResponse(sale.id(), sale.folio(), sale.channel().name(), sale.externalReference(), sale.status().name(),
                sale.branchId(), sale.warehouseId(), sale.currency(), sale.total(), sale.received(), sale.change(),
                sale.lines().stream().map(LineResponse::from).toList(), sale.payments().stream().map(PaymentResponse::from).toList(),
                sale.actorUserId(), sale.createdAt(), sale.completedAt(), sale.cancelledAt(), sale.cancelReason());
    }

    public record LineResponse(int lineNumber, UUID productId, String sku, String name, BigDecimal quantity,
                               BigDecimal unitPrice, BigDecimal lineTotal) {
        static LineResponse from(SaleLine line) {
            return new LineResponse(line.lineNumber(), line.productId(), line.sku(), line.name(), line.quantity(),
                    line.unitPrice(), line.lineTotal());
        }
    }

    public record PaymentResponse(String method, BigDecimal amount, String reference) {
        static PaymentResponse from(Payment payment) {
            return new PaymentResponse(payment.method().name(), payment.amount(), payment.reference());
        }
    }
}
