package com.jcode.jcomercy.sales.application.service;

import com.jcode.jcomercy.sales.application.port.SaleEventsPort;
import com.jcode.jcomercy.sales.domain.model.sale.Sale;
import com.jcode.jcomercy.sales.domain.port.FolioGeneratorPort;
import com.jcode.jcomercy.sales.domain.port.SaleRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Cierra una venta ya confirmada en inventario: toma el siguiente folio de la sucursal y guarda la venta completada, en
 * una sola transacción corta. Va aparte del cobro porque este llama a inventario por red y no debe tener una
 * transacción abierta (con el folio bloqueado) mientras espera. Si algo falla aquí, el folio no se gasta.
 */
public class SaleCompletionService {
    private static final Logger log = LoggerFactory.getLogger(SaleCompletionService.class);

    private final SaleRepositoryPort sales;
    private final FolioGeneratorPort folios;
    private final SaleEventsPort events;
    private final Clock clock;

    public SaleCompletionService(SaleRepositoryPort sales, FolioGeneratorPort folios, SaleEventsPort events, Clock clock) {
        this.sales = sales;
        this.folios = folios;
        this.events = events;
        this.clock = clock;
    }

    /** El folio, la venta completada y su aviso van juntos: si cualquiera falla, ninguno queda. */
    @Transactional
    public Sale complete(Sale confirming, String correlationId) {
        String folio = folios.next(confirming.companyId(), confirming.branchId());
        Sale done = sales.save(confirming.complete(folio, clock.instant()));
        events.completed(done, correlationId);
        log.info("Venta completada saleId={} folio={} branchId={} total={}", done.id(), done.folio(), done.branchId(), done.total());
        return done;
    }
}
