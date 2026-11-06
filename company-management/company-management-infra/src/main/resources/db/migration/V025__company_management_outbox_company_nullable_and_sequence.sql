-- V025: el Outbox deja de guardar valores falsos en el sobre de los eventos (ERR-024).
--
-- 1) Los eventos de cuenta ocurren antes de que exista una empresa: su company_id es nulo, no una cadena vacía.
ALTER TABLE company_management.outbox_event ALTER COLUMN company_id DROP NOT NULL;

UPDATE company_management.outbox_event SET company_id = NULL WHERE company_id = '';

-- 2) Los eventos de sucursal pendientes que se guardaron con account_id vacío lo recuperan de la empresa dueña.
UPDATE company_management.outbox_event
SET account_id = (
    SELECT c.public_account_id FROM company_management.company c WHERE c.public_company_id = outbox_event.company_id
)
WHERE account_id = ''
  AND company_id IS NOT NULL
  AND EXISTS (
    SELECT 1 FROM company_management.company c WHERE c.public_company_id = outbox_event.company_id
);

-- 3) «sequence» deja de ser una constante (1 al crear, 2 al activar): crece con el orden en que se registran los eventos.
--    Puede tener huecos (una transacción revertida gasta su número) y no garantiza el orden de confirmación entre
--    transacciones concurrentes; sirve para que un consumidor ordene y detecte repetidos.
CREATE SEQUENCE company_management.outbox_event_sequence START WITH 1 INCREMENT BY 1;
