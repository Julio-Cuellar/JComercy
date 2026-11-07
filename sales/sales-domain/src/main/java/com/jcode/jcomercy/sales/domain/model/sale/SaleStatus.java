package com.jcode.jcomercy.sales.domain.model.sale;

/**
 * El estado de una venta. PENDING: el stock está apartado. CONFIRMING: el pago está registrado y falta confirmar la
 * salida de inventario. COMPLETED: vendida, con folio. CANCELLED: cancelada. Un estado de atención para ventas de
 * otros canales sin stock suficiente queda reservado en el contrato y todavía no existe.
 */
public enum SaleStatus {
    PENDING,
    CONFIRMING,
    COMPLETED,
    CANCELLED
}
