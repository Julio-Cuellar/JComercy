package com.jcode.jcomercy.sales.application.service;

import com.jcode.jcomercy.sales.application.exception.InventoryRejectedException;
import com.jcode.jcomercy.sales.application.exception.SalesApplicationException;
import com.jcode.jcomercy.sales.application.port.InventoryGatewayPort;
import com.jcode.jcomercy.sales.application.port.InventoryGatewayPort.Caller;
import com.jcode.jcomercy.sales.application.port.InventoryGatewayPort.StockReference;
import com.jcode.jcomercy.sales.domain.exception.SalesDomainException;
import com.jcode.jcomercy.sales.domain.model.sale.Sale;
import com.jcode.jcomercy.sales.domain.model.sale.SaleLine;
import com.jcode.jcomercy.sales.domain.port.SaleRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Cancela una venta completa. Una pendiente libera lo que tenía apartado; una completada devuelve el stock de cada línea
 * a inventario; una ya cancelada o que se está confirmando se rechaza. Como el cobro, no es transaccional (llama a
 * inventario por red) y es seguro de repetir: cada llamada lleva una llave que sale de la venta y la línea, así que si
 * inventario falla a medias la venta sigue completada y repetir la cancelación la termina sin devolver dos veces.
 */
public class SaleCancellationService {
    private static final Logger log = LoggerFactory.getLogger(SaleCancellationService.class);
    private static final String REFERENCE_TYPE = "SALE";
    /** Lo que inventario responde cuando esa referencia ya se devolvió: el resultado buscado ya está hecho. */
    private static final String ALREADY_RETURNED = "already been returned";

    private final SaleRepositoryPort sales;
    private final InventoryGatewayPort inventory;
    private final SaleCancellationRecorder recorder;

    public SaleCancellationService(SaleRepositoryPort sales, InventoryGatewayPort inventory, SaleCancellationRecorder recorder) {
        this.sales = sales;
        this.inventory = inventory;
        this.recorder = recorder;
    }

    public Sale cancel(CancelCommand command) {
        // El motivo se pide antes de tocar inventario: si faltara al final, el stock ya habría vuelto.
        if (command.reason() == null || command.reason().isBlank()) {
            throw new SalesDomainException("Anota el motivo de la cancelación.");
        }
        Sale sale = sales.findById(command.companyId(), command.branchId(), command.saleId())
                .orElseThrow(() -> SalesApplicationException.notFound("No encontramos esa venta en esta sucursal."));
        switch (sale.status()) {
            case CANCELLED -> throw SalesApplicationException.conflict("La venta ya está cancelada.");
            case CONFIRMING -> throw SalesApplicationException.conflict(
                    "Esta venta se está confirmando. Espera a que termine para cancelarla.");
            case PENDING -> releaseAll(sale, command);
            case COMPLETED -> returnAll(sale, command);
        }
        return recorder.record(sale, command.reason(), command.actorUserId(), command.caller().correlationId());
    }

    private void releaseAll(Sale sale, CancelCommand command) {
        for (SaleLine line : sale.lines()) {
            try {
                inventory.release(command.caller(), reference(sale, line, "release", command.reason()));
            } catch (InventoryRejectedException e) {
                // El apartado trae un plazo: si ya venció, inventario lo liberó solo y no hay nada que liberar.
                log.warn("No había apartado que liberar de {} en la venta saleId={}: {}", line.sku(), sale.id(), e.getMessage());
            }
        }
    }

    private void returnAll(Sale sale, CancelCommand command) {
        for (SaleLine line : sale.lines()) {
            try {
                inventory.returnStock(command.caller(), reference(sale, line, "return", command.reason()));
            } catch (InventoryRejectedException e) {
                if (e.getMessage() != null && e.getMessage().contains(ALREADY_RETURNED)) {
                    log.info("El stock de {} de la venta saleId={} ya estaba devuelto", line.sku(), sale.id());
                    continue;
                }
                throw SalesApplicationException.conflict(
                        "Inventario no pudo devolver el stock de %s: %s. La venta sigue completada.".formatted(line.sku(), e.getMessage()));
            }
        }
    }

    private static StockReference reference(Sale sale, SaleLine line, String operation, String reason) {
        return new StockReference(sale.companyId(), sale.branchId(), REFERENCE_TYPE, sale.id() + ":" + line.lineNumber(), reason,
                "sale-%s-%s-%d".formatted(sale.id(), operation, line.lineNumber()));
    }

    public record CancelCommand(String companyId, String branchId, UUID saleId, String reason, String actorUserId, Caller caller) { }
}
