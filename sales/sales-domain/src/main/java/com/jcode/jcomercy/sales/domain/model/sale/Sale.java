package com.jcode.jcomercy.sales.domain.model.sale;

import com.jcode.jcomercy.sales.domain.exception.SalesDomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Una venta, igual para todos los canales. Nace pendiente, con el stock por apartar; al registrarse el pago pasa a
 * confirmándose; al confirmarse la salida de inventario se completa y recibe su folio; y se puede cancelar. Es
 * inmutable: cada paso devuelve otra venta. El folio se da al completarla, para que los folios de una sucursal no
 * tengan huecos por ventas que no llegaron a cerrarse.
 */
public record Sale(
        UUID id,
        String companyId,
        String branchId,
        UUID warehouseId,
        SaleChannel channel,
        String externalReference,
        SaleStatus status,
        String currency,
        List<SaleLine> lines,
        List<Payment> payments,
        String folio,
        BigDecimal total,
        BigDecimal received,
        BigDecimal change,
        String actorUserId,
        Instant createdAt,
        Instant completedAt,
        Instant cancelledAt,
        String cancelReason
) {
    private static final Pattern CURRENCY = Pattern.compile("[A-Za-z]{3}");
    /** Un número de tarjeta tiene de 13 a 19 dígitos; una referencia con 13 o más se trata como uno. */
    private static final int CARD_NUMBER_MIN_DIGITS = 13;

    public Sale {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");
        lines = List.copyOf(lines);
        payments = List.copyOf(payments);
    }

    /** Arma una venta pendiente con sus líneas y su total. */
    public static Sale create(UUID id, String companyId, String branchId, UUID warehouseId, SaleChannel channel,
                              String externalReference, String currency, List<SaleLine> lines, String actorUserId,
                              Instant now) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(now, "now must not be null");
        required(companyId, "la empresa");
        required(branchId, "la sucursal");
        required(actorUserId, "quien vende");
        if (warehouseId == null) {
            throw new SalesDomainException("Falta el almacén de donde sale la mercancía.");
        }
        if (channel == null) {
            throw new SalesDomainException("Falta el canal de la venta.");
        }
        if (currency == null || !CURRENCY.matcher(currency.trim()).matches()) {
            throw new SalesDomainException("La moneda debe ser un código de tres letras, por ejemplo MXN.");
        }
        if (lines == null || lines.isEmpty()) {
            throw new SalesDomainException("La venta necesita al menos un producto.");
        }
        Set<UUID> products = new HashSet<>();
        for (SaleLine line : lines) {
            if (!products.add(line.productId())) {
                throw new SalesDomainException("%s aparece en dos líneas. Junta las cantidades en una sola.".formatted(line.sku()));
            }
        }
        List<SaleLine> numbered = IntStream.range(0, lines.size()).mapToObj(i -> lines.get(i).numbered(i + 1)).toList();
        BigDecimal total = numbered.stream().map(SaleLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        String reference = externalReference == null || externalReference.isBlank() ? null : externalReference.trim();
        return new Sale(id, companyId.trim(), branchId.trim(), warehouseId, channel, reference, SaleStatus.PENDING,
                currency.trim().toUpperCase(), numbered, List.of(), null, total, zero(), zero(), actorUserId.trim(), now,
                null, null, null);
    }

    /**
     * Registra el cobro y deja la venta confirmándose. El efectivo da cambio; la tarjeta y la transferencia no pueden
     * pasar del total, así que el cambio siempre sale del efectivo.
     */
    public Sale registerPayments(List<Payment> newPayments) {
        if (status != SaleStatus.PENDING) {
            throw new SalesDomainException("Esta venta ya no admite pagos: está en %s.".formatted(status));
        }
        if (newPayments == null || newPayments.isEmpty()) {
            throw new SalesDomainException("Registra al menos un pago.");
        }
        for (Payment payment : newPayments) {
            if (payment.amount().signum() <= 0) {
                throw new SalesDomainException("Cada pago debe ser mayor que cero.");
            }
            if (looksLikeACardNumber(payment.reference())) {
                throw new SalesDomainException("La referencia del pago parece un número de tarjeta. "
                        + "Anota solo la autorización, nunca el número de la tarjeta.");
            }
        }
        BigDecimal nonCash = newPayments.stream().filter(payment -> !payment.isCash()).map(Payment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (nonCash.compareTo(total) > 0) {
            throw new SalesDomainException(
                    "La tarjeta y la transferencia no pueden pasar del total de %s. Cobra el excedente en efectivo."
                            .formatted(total.toPlainString()));
        }
        BigDecimal receivedNow = newPayments.stream().map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (receivedNow.compareTo(total) < 0) {
            throw new SalesDomainException("Faltan %s para cubrir el total de %s."
                    .formatted(total.subtract(receivedNow).toPlainString(), total.toPlainString()));
        }
        return new Sale(id, companyId, branchId, warehouseId, channel, externalReference, SaleStatus.CONFIRMING, currency,
                lines, newPayments, folio, total, receivedNow, receivedNow.subtract(total), actorUserId, createdAt,
                completedAt, cancelledAt, cancelReason);
    }

    /** Cierra la venta: la salida de inventario ya se confirmó y la venta recibe su folio. */
    public Sale complete(String newFolio, Instant now) {
        if (status != SaleStatus.CONFIRMING) {
            throw new SalesDomainException(
                    "Solo se completa una venta con el pago registrado; esta está en %s.".formatted(status));
        }
        if (newFolio == null || newFolio.isBlank()) {
            throw new SalesDomainException("El folio es obligatorio para completar la venta.");
        }
        Objects.requireNonNull(now, "now must not be null");
        return new Sale(id, companyId, branchId, warehouseId, channel, externalReference, SaleStatus.COMPLETED, currency,
                lines, payments, newFolio.trim(), total, received, change, actorUserId, createdAt, now, cancelledAt,
                cancelReason);
    }

    /** Cancela la venta, pendiente o completada, con su motivo. Una que se está confirmando espera a terminar. */
    public Sale cancel(String reason, Instant now) {
        if (reason == null || reason.isBlank()) {
            throw new SalesDomainException("Anota el motivo de la cancelación.");
        }
        if (status == SaleStatus.CANCELLED) {
            throw new SalesDomainException("La venta ya está cancelada.");
        }
        if (status == SaleStatus.CONFIRMING) {
            throw new SalesDomainException("Esta venta se está confirmando. Espera a que termine para cancelarla.");
        }
        Objects.requireNonNull(now, "now must not be null");
        return new Sale(id, companyId, branchId, warehouseId, channel, externalReference, SaleStatus.CANCELLED, currency,
                lines, payments, folio, total, received, change, actorUserId, createdAt, completedAt, now, reason.trim());
    }

    private static boolean looksLikeACardNumber(String reference) {
        return reference != null && reference.chars().filter(Character::isDigit).count() >= CARD_NUMBER_MIN_DIGITS;
    }

    private static void required(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new SalesDomainException("Falta %s de la venta.".formatted(what));
        }
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }
}
