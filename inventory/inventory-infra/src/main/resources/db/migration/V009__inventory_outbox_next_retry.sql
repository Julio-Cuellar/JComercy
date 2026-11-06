-- V009: espera entre reintentos del Outbox de inventario (ERR-022).
-- Hasta ahora cada ciclo reintentaba los 50 eventos pendientes más antiguos: si fallaban siempre, los más nuevos no se
-- intentaban nunca. Con next_retry_at, un evento que el broker rechaza espera (5 s por intento, hasta 300 s), como en
-- company-management, y el ciclo sigue con los demás.
ALTER TABLE inventory.outbox_event ADD COLUMN next_retry_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_inventory_outbox_dispatch ON inventory.outbox_event(status, next_retry_at, created_at);
