CREATE TABLE inventory.inventory_serial_sequence (
    company_id VARCHAR(100) PRIMARY KEY,
    next_value BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_inventory_serial_sequence_next_value CHECK (next_value > 0)
);

CREATE UNIQUE INDEX uk_stock_company_serial
    ON inventory.stock_balance (company_id, serial_number);
