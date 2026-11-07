-- V003: la cuenta de folios de cada sucursal. Cada ticket completado toma el siguiente número; la fila se bloquea al
-- tomarlo para que dos cobros a la vez no repitan folio.
CREATE TABLE sales.ticket_sequence (
    company_id VARCHAR(100) NOT NULL,
    branch_id VARCHAR(100) NOT NULL,
    next_value BIGINT NOT NULL,
    CONSTRAINT pk_ticket_sequence PRIMARY KEY (company_id, branch_id),
    CONSTRAINT ck_ticket_sequence_next CHECK (next_value >= 1)
);
