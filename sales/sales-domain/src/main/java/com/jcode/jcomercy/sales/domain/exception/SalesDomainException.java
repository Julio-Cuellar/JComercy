package com.jcode.jcomercy.sales.domain.exception;

/** Una regla de la venta que no se cumple; el mensaje está escrito para quien vende y dice qué corregir. */
public class SalesDomainException extends RuntimeException {
    public SalesDomainException(String message) {
        super(message);
    }
}
