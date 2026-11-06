-- V010: «sequence» en los eventos de Inventory, como en company-management (ERR-026).
-- Crece con el orden en que se registran los eventos. Puede tener huecos (una transacción revertida gasta su número) y
-- no garantiza el orden de confirmación entre transacciones concurrentes; sirve para que un consumidor ordene y
-- descarte repetidos.
CREATE SEQUENCE inventory.outbox_event_sequence START WITH 1 INCREMENT BY 1;

-- Los eventos registrados antes de esta migración llevan sequence 0: son anteriores a la secuencia y la mayoría ya se
-- publicó; numerarlos uno a uno recorrería toda la tabla.
ALTER TABLE inventory.outbox_event ADD COLUMN sequence BIGINT NOT NULL DEFAULT 0;
