-- V011: un saldo de stock se identifica por almacén, ubicación, producto, lote, serie y estado (ERR-017).
-- En un producto sin lote ni serie, lote y serie son nulos, y para una restricción única normal dos nulos son
-- distintos: la base de datos dejaba entrar dos saldos de la misma mercancía, y solo el bloqueo de la ubicación en la
-- aplicación lo evitaba. Con NULLS NOT DISTINCT dos nulos cuentan como iguales.
--
-- Requiere PostgreSQL 15 o más (H2 2.2 también lo admite).
--
-- Si ya existen saldos duplicados, la migración falla a propósito, porque fusionarlos es una decisión de negocio. Para
-- encontrarlos antes de migrar:
--   SELECT warehouse_id, location_id, product_id, lot_id, serial_number, inventory_status, COUNT(*)
--   FROM inventory.stock_balance
--   GROUP BY warehouse_id, location_id, product_id, lot_id, serial_number, inventory_status
--   HAVING COUNT(*) > 1;
ALTER TABLE inventory.stock_balance DROP CONSTRAINT uk_stock_identity;

ALTER TABLE inventory.stock_balance
    ADD CONSTRAINT uk_stock_identity
    UNIQUE NULLS NOT DISTINCT (warehouse_id, location_id, product_id, lot_id, serial_number, inventory_status);
