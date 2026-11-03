CREATE TABLE inventory.stock_reservation (
    id UUID PRIMARY KEY,
    public_reservation_id UUID NOT NULL UNIQUE,
    company_id VARCHAR(100) NOT NULL,
    branch_id VARCHAR(100) NOT NULL,
    warehouse_id UUID NOT NULL,
    location_id UUID NOT NULL,
    product_id UUID NOT NULL,
    stock_balance_id UUID NOT NULL,
    quantity DECIMAL(24, 9) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reference_type VARCHAR(100) NOT NULL,
    reference_id VARCHAR(150) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    actor_user_id VARCHAR(100) NOT NULL,
    correlation_id VARCHAR(150) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_reservation_warehouse FOREIGN KEY (warehouse_id) REFERENCES inventory.warehouse(id),
    CONSTRAINT fk_reservation_location FOREIGN KEY (location_id) REFERENCES inventory.warehouse_location(id),
    CONSTRAINT fk_reservation_product FOREIGN KEY (product_id) REFERENCES inventory.product(id),
    CONSTRAINT fk_reservation_balance FOREIGN KEY (stock_balance_id) REFERENCES inventory.stock_balance(id),
    CONSTRAINT ck_reservation_quantity CHECK (quantity > 0),
    CONSTRAINT ck_reservation_status CHECK (status IN ('ACTIVE', 'COMMITTED', 'RELEASED', 'EXPIRED'))
);

CREATE INDEX ix_reservation_reference ON inventory.stock_reservation (company_id, reference_type, reference_id, status);
CREATE INDEX ix_reservation_due ON inventory.stock_reservation (status, expires_at);
CREATE INDEX ix_reservation_balance ON inventory.stock_reservation (stock_balance_id, status);
