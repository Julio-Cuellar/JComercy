package com.jcode.jcomercy.sales.domain.port;

import com.jcode.jcomercy.sales.domain.model.sale.Sale;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Las ventas. Se buscan siempre con su empresa y su sucursal: una venta de otra sucursal no se encuentra, igual que una
 * que no existe. Guardar crea la venta con sus líneas o actualiza su avance (pagos, folio, cancelación).
 */
public interface SaleRepositoryPort {

    Sale save(Sale sale);

    /**
     * Crea la venta y le asocia la llave de idempotencia de la petición que la originó. Una llave solo puede crear una
     * venta por sucursal: si ya existe, lanza {@code DuplicateSaleException}.
     */
    Sale create(Sale sale, String idempotencyKey);

    Optional<Sale> findByIdempotencyKey(String companyId, String branchId, String idempotencyKey);

    Optional<Sale> findById(String companyId, String branchId, UUID id);

    /** Las ventas más recientes de la sucursal, de la más reciente a la más antigua. */
    List<Sale> findByBranch(String companyId, String branchId);
}
