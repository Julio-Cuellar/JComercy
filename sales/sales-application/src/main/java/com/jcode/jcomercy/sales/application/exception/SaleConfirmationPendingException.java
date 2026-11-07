package com.jcode.jcomercy.sales.application.exception;

import java.util.UUID;

/**
 * La venta quedó registrada y cobrada pero falta confirmar la salida de inventario, porque inventario no respondió.
 * Repetir la misma petición, con la misma llave y la misma persona, la termina sin duplicar nada.
 */
public class SaleConfirmationPendingException extends RuntimeException {
    private final UUID saleId;

    public SaleConfirmationPendingException(UUID saleId) {
        super("La venta quedó registrada pero falta confirmar la salida de inventario. "
                + "Repite la misma petición con la misma llave para terminarla.");
        this.saleId = saleId;
    }

    public UUID saleId() {
        return saleId;
    }
}
