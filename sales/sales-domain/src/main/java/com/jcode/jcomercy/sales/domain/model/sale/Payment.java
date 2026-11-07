package com.jcode.jcomercy.sales.domain.model.sale;

import com.jcode.jcomercy.sales.domain.exception.SalesDomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Un pago de la venta. La referencia es opcional y sirve para la autorización de una tarjeta o de una transferencia;
 * nunca es el número de la tarjeta.
 */
public record Payment(PaymentMethod method, BigDecimal amount, String reference) {

    public Payment {
        if (method == null) {
            throw new SalesDomainException("Indica cómo se pagó: efectivo, tarjeta o transferencia.");
        }
        if (amount == null) {
            throw new SalesDomainException("Cada pago necesita su importe.");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        reference = reference == null || reference.isBlank() ? null : reference.trim();
    }

    public boolean isCash() {
        return method == PaymentMethod.CASH;
    }
}
