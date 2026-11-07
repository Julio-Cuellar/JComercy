package com.jcode.jcomercy.sales.domain.exception;

/** La venta ya estaba registrada: misma referencia externa en el mismo canal, o el mismo folio en la sucursal. */
public class DuplicateSaleException extends SalesDomainException {
    public DuplicateSaleException() {
        super("Esa venta ya está registrada.");
    }
}
