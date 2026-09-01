CREATE TABLE IF NOT EXISTS company_management.idempotency_key (
    id UUID PRIMARY KEY,
    operation VARCHAR(120) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ux_idempotency_scope_key UNIQUE (operation, idempotency_key)
);
