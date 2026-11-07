-- V006: el Outbox de ventas. Un evento se guarda en la misma transacción que cambia la venta, y un despachador aparte lo
-- publica al broker; así registrar un evento no depende de que el broker esté arriba y ninguno se pierde.
-- «sequence» crece con el orden en que se registran los eventos. Puede tener huecos (una transacción revertida gasta su
-- número) y no garantiza el orden de confirmación entre transacciones concurrentes; sirve para que un consumidor ordene y
-- descarte repetidos. «next_retry_at» es la espera de un evento que el broker rechazó, para que no tape a los demás.
CREATE SEQUENCE sales.outbox_event_sequence START WITH 1 INCREMENT BY 1;

CREATE TABLE sales.outbox_event (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    schema_version VARCHAR(20) NOT NULL,
    company_id VARCHAR(100) NOT NULL,
    branch_id VARCHAR(100),
    actor_user_id VARCHAR(100) NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    sequence BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_sales_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED'))
);

CREATE INDEX idx_sales_outbox_dispatch ON sales.outbox_event (status, next_retry_at, sequence);
