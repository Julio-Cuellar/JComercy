package com.jcode.jcomercy.sales.application.service;

import com.jcode.jcomercy.sales.application.port.SaleEventsPort;
import com.jcode.jcomercy.sales.domain.model.sale.Sale;
import com.jcode.jcomercy.sales.domain.model.sale.SaleStatus;
import com.jcode.jcomercy.sales.domain.port.SaleRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Deja la venta cancelada y, si estaba completada, el aviso de que se canceló, en una sola transacción corta. Va aparte
 * de la cancelación porque esta llama a inventario por red y no debe tener una transacción abierta mientras espera.
 */
public class SaleCancellationRecorder {
    private static final Logger log = LoggerFactory.getLogger(SaleCancellationRecorder.class);

    private final SaleRepositoryPort sales;
    private final SaleEventsPort events;
    private final Clock clock;

    public SaleCancellationRecorder(SaleRepositoryPort sales, SaleEventsPort events, Clock clock) {
        this.sales = sales;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Sale record(Sale sale, String reason, String actorUserId, String correlationId) {
        Sale cancelled = sales.save(sale.cancel(reason, clock.instant()));
        if (sale.status() == SaleStatus.COMPLETED) {
            events.cancelled(cancelled, actorUserId, correlationId);
        }
        log.info("Venta cancelada saleId={} folio={} estadoAnterior={} actor={}", cancelled.id(), cancelled.folio(), sale.status(),
                actorUserId);
        return cancelled;
    }
}
