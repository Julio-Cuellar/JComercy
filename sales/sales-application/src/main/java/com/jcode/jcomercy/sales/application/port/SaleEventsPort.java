package com.jcode.jcomercy.sales.application.port;

import com.jcode.jcomercy.sales.domain.model.sale.Sale;

/**
 * Los avisos que Ventas deja para quien quiera enterarse de lo que ya ocurrió (contabilidad, notificaciones, reportes):
 * una venta completada y una venta completada que luego se cancela. Se dejan en el Outbox, en la misma transacción que
 * cambia la venta, así que no se pierden ni se emiten sin que la venta haya cambiado de verdad. Ver {@code asyncapi-sales-v1.yaml}.
 */
public interface SaleEventsPort {

    /** La venta acaba de completarse: ya tiene folio y su mercancía salió del almacén. Quien la hizo es quien vendió. */
    void completed(Sale sale, String correlationId);

    /** Una venta que estaba completada acaba de cancelarse. Las ventas que se cancelan antes de completarse no se avisan. */
    void cancelled(Sale sale, String actorUserId, String correlationId);
}
