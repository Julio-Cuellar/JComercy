-- V012: buscar los movimientos de una referencia (por ejemplo para devolver una venta cancelada) no debe recorrer toda la
-- tabla de movimientos.
CREATE INDEX idx_inventory_movement_reference
    ON inventory.inventory_movement (company_id, reference_type, reference_id);
