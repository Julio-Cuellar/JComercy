CREATE SCHEMA IF NOT EXISTS inventory;

CREATE TABLE inventory.unit_of_measure (
    code VARCHAR(20) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    dimension VARCHAR(30) NOT NULL,
    base_unit_code VARCHAR(20) NOT NULL,
    conversion_factor DECIMAL(24, 9) NOT NULL,
    decimals INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT ck_uom_factor_positive CHECK (conversion_factor > 0),
    CONSTRAINT ck_uom_decimals_range CHECK (decimals BETWEEN 0 AND 9),
    CONSTRAINT ck_uom_dimension CHECK (dimension IN ('MASS', 'VOLUME', 'LENGTH', 'AREA', 'COUNT', 'TIME')),
    CONSTRAINT ck_uom_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE inventory.product (
    id UUID PRIMARY KEY,
    public_product_id UUID NOT NULL UNIQUE,
    company_id VARCHAR(100) NOT NULL,
    sku VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    category_id VARCHAR(100),
    stock_unit_code VARCHAR(20) NOT NULL,
    purchase_unit_code VARCHAR(20),
    sale_unit_code VARCHAR(20),
    net_quantity DECIMAL(24, 9),
    net_quantity_unit_code VARCHAR(20),
    tracking_mode VARCHAR(30) NOT NULL,
    allow_negative_stock BOOLEAN NOT NULL DEFAULT FALSE,
    costing_method VARCHAR(40) NOT NULL DEFAULT 'DEFAULT',
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_product_company_sku UNIQUE (company_id, sku),
    CONSTRAINT fk_product_stock_uom FOREIGN KEY (stock_unit_code) REFERENCES inventory.unit_of_measure(code),
    CONSTRAINT fk_product_purchase_uom FOREIGN KEY (purchase_unit_code) REFERENCES inventory.unit_of_measure(code),
    CONSTRAINT fk_product_sale_uom FOREIGN KEY (sale_unit_code) REFERENCES inventory.unit_of_measure(code),
    CONSTRAINT fk_product_net_uom FOREIGN KEY (net_quantity_unit_code) REFERENCES inventory.unit_of_measure(code),
    CONSTRAINT ck_product_tracking_mode CHECK (tracking_mode IN ('NONE', 'LOT', 'SERIAL', 'LOT_AND_EXPIRATION', 'SERIAL_AND_EXPIRATION')),
    CONSTRAINT ck_product_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_product_net_quantity CHECK (net_quantity IS NULL OR net_quantity > 0)
);

CREATE TABLE inventory.product_unit_conversion (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    from_unit_code VARCHAR(20) NOT NULL,
    to_unit_code VARCHAR(20) NOT NULL,
    factor DECIMAL(24, 9) NOT NULL,
    rounding_mode VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT uk_product_conversion UNIQUE (product_id, from_unit_code, to_unit_code),
    CONSTRAINT fk_conversion_product FOREIGN KEY (product_id) REFERENCES inventory.product(id),
    CONSTRAINT fk_conversion_from_uom FOREIGN KEY (from_unit_code) REFERENCES inventory.unit_of_measure(code),
    CONSTRAINT fk_conversion_to_uom FOREIGN KEY (to_unit_code) REFERENCES inventory.unit_of_measure(code),
    CONSTRAINT ck_conversion_factor_positive CHECK (factor > 0),
    CONSTRAINT ck_conversion_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE inventory.warehouse (
    id UUID PRIMARY KEY,
    public_warehouse_id UUID NOT NULL UNIQUE,
    company_id VARCHAR(100) NOT NULL,
    scope VARCHAR(30) NOT NULL,
    branch_id VARCHAR(100),
    code VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    address VARCHAR(1000),
    phone VARCHAR(50),
    email VARCHAR(254),
    manager_id VARCHAR(100),
    timezone VARCHAR(100) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_warehouse_company_code UNIQUE (company_id, code),
    CONSTRAINT ck_warehouse_scope CHECK (scope IN ('BRANCH_OWNED', 'COMPANY_SHARED')),
    CONSTRAINT ck_warehouse_branch_scope CHECK ((scope = 'BRANCH_OWNED' AND branch_id IS NOT NULL) OR (scope = 'COMPANY_SHARED' AND branch_id IS NULL)),
    CONSTRAINT ck_warehouse_type CHECK (type IN ('GENERAL', 'RECEIVING', 'DISPATCH', 'TRANSIT', 'VIRTUAL')),
    CONSTRAINT ck_warehouse_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'BLOCKED'))
);

CREATE TABLE inventory.warehouse_branch_access (
    id UUID PRIMARY KEY,
    warehouse_id UUID NOT NULL,
    branch_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    assigned_by VARCHAR(100) NOT NULL,
    CONSTRAINT uk_warehouse_branch_access UNIQUE (warehouse_id, branch_id),
    CONSTRAINT fk_access_warehouse FOREIGN KEY (warehouse_id) REFERENCES inventory.warehouse(id),
    CONSTRAINT ck_access_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE inventory.warehouse_location (
    id UUID PRIMARY KEY,
    public_location_id UUID NOT NULL UNIQUE,
    warehouse_id UUID NOT NULL,
    parent_location_id UUID,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(200),
    type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    capacity DECIMAL(24, 9),
    capacity_unit_code VARCHAR(20),
    CONSTRAINT uk_location_warehouse_code UNIQUE (warehouse_id, code),
    CONSTRAINT fk_location_warehouse FOREIGN KEY (warehouse_id) REFERENCES inventory.warehouse(id),
    CONSTRAINT fk_location_parent FOREIGN KEY (parent_location_id) REFERENCES inventory.warehouse_location(id),
    CONSTRAINT fk_location_capacity_uom FOREIGN KEY (capacity_unit_code) REFERENCES inventory.unit_of_measure(code),
    CONSTRAINT ck_location_type CHECK (type IN ('INTERNAL', 'RECEIVING', 'PICKING', 'DISPATCH', 'TRANSIT', 'SUPPLIER', 'CUSTOMER', 'INVENTORY_LOSS', 'QUARANTINE', 'CUSTOM')),
    CONSTRAINT ck_location_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED')),
    CONSTRAINT ck_location_capacity CHECK (capacity IS NULL OR capacity > 0)
);

CREATE TABLE inventory.stock_balance (
    id UUID PRIMARY KEY,
    company_id VARCHAR(100) NOT NULL,
    branch_id VARCHAR(100) NOT NULL,
    warehouse_id UUID NOT NULL,
    location_id UUID NOT NULL,
    product_id UUID NOT NULL,
    lot_id VARCHAR(100),
    serial_number VARCHAR(150),
    expiration_date DATE,
    inventory_status VARCHAR(20) NOT NULL,
    on_hand DECIMAL(24, 9) NOT NULL,
    reserved DECIMAL(24, 9) NOT NULL,
    unit_cost DECIMAL(24, 9),
    costing_method VARCHAR(40),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_stock_identity UNIQUE (warehouse_id, location_id, product_id, lot_id, serial_number, inventory_status),
    CONSTRAINT fk_stock_warehouse FOREIGN KEY (warehouse_id) REFERENCES inventory.warehouse(id),
    CONSTRAINT fk_stock_location FOREIGN KEY (location_id) REFERENCES inventory.warehouse_location(id),
    CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES inventory.product(id),
    CONSTRAINT ck_stock_status CHECK (inventory_status IN ('AVAILABLE', 'QUARANTINED', 'DAMAGED')),
    CONSTRAINT ck_stock_reserved CHECK (reserved >= 0),
    CONSTRAINT ck_stock_cost CHECK (unit_cost IS NULL OR unit_cost >= 0)
);

CREATE TABLE inventory.inventory_movement (
    id UUID PRIMARY KEY,
    public_movement_id UUID NOT NULL UNIQUE,
    company_id VARCHAR(100) NOT NULL,
    branch_id VARCHAR(100) NOT NULL,
    warehouse_id UUID NOT NULL,
    location_id UUID NOT NULL,
    product_id UUID NOT NULL,
    type VARCHAR(30) NOT NULL,
    quantity DECIMAL(24, 9) NOT NULL,
    unit_code VARCHAR(20) NOT NULL,
    unit_cost DECIMAL(24, 9),
    total_cost DECIMAL(24, 9),
    lot_id VARCHAR(100),
    serial_number VARCHAR(150),
    expiration_date DATE,
    reference_type VARCHAR(100),
    reference_id VARCHAR(150),
    reason VARCHAR(500),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_user_id VARCHAR(100) NOT NULL,
    correlation_id VARCHAR(150) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_movement_warehouse FOREIGN KEY (warehouse_id) REFERENCES inventory.warehouse(id),
    CONSTRAINT fk_movement_location FOREIGN KEY (location_id) REFERENCES inventory.warehouse_location(id),
    CONSTRAINT fk_movement_product FOREIGN KEY (product_id) REFERENCES inventory.product(id),
    CONSTRAINT fk_movement_unit FOREIGN KEY (unit_code) REFERENCES inventory.unit_of_measure(code),
    CONSTRAINT ck_movement_type CHECK (type IN ('RECEIPT', 'ISSUE', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'TRANSFER_OUT', 'TRANSFER_IN', 'RETURN', 'KIT_BREAKDOWN_OUT', 'KIT_BREAKDOWN_IN')),
    CONSTRAINT ck_movement_quantity CHECK (quantity > 0),
    CONSTRAINT ck_movement_unit_cost CHECK (unit_cost IS NULL OR unit_cost >= 0),
    CONSTRAINT ck_movement_total_cost CHECK (total_cost IS NULL OR total_cost >= 0)
);

CREATE INDEX ix_product_company ON inventory.product (company_id);
CREATE INDEX ix_stock_product ON inventory.stock_balance (company_id, product_id);
CREATE INDEX ix_stock_warehouse ON inventory.stock_balance (warehouse_id, location_id);
CREATE INDEX ix_movement_product_date ON inventory.inventory_movement (company_id, product_id, occurred_at);
