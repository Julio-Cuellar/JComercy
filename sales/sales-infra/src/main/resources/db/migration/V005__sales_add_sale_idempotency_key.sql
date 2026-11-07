-- V005: la llave de idempotencia de la petición que originó cada venta. Repetir la petición con la misma llave encuentra la
-- venta que ya se hizo (y la retoma si quedó a medias) en lugar de crear otra. Una llave solo puede crear una venta por
-- sucursal. Las ventas sin llave (las de un canal externo) la dejan nula, y los nulos no chocan entre sí.
ALTER TABLE sales.sale ADD COLUMN idempotency_key VARCHAR(100);

ALTER TABLE sales.sale
    ADD CONSTRAINT uq_sale_idempotency_key UNIQUE (company_id, branch_id, idempotency_key);
