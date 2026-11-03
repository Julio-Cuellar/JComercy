CREATE TABLE inventory.idempotency_key (
    id UUID PRIMARY KEY,
    company_id VARCHAR(100) NOT NULL,
    actor_user_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_status INT,
    response_body TEXT,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_inventory_idempotency_scope_key UNIQUE (company_id, actor_user_id, idempotency_key)
);
