CREATE TABLE IF NOT EXISTS core.inventory_suppliers (
    active boolean NOT NULL DEFAULT true,
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    clinic_id uuid NOT NULL,
    id uuid NOT NULL,
    name varchar(255) NOT NULL,
    contact_name varchar(255),
    phone varchar(100),
    email varchar(255),
    tax_id varchar(100),
    notes TEXT,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_inventory_suppliers_clinic
    ON core.inventory_suppliers (clinic_id, active, name);

CREATE TABLE IF NOT EXISTS core.purchase_orders (
    order_date date NOT NULL,
    expected_date date,
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    clinic_id uuid NOT NULL,
    id uuid NOT NULL,
    supplier_id uuid NOT NULL,
    folio varchar(255) NOT NULL,
    supplier_name varchar(255) NOT NULL,
    status varchar(50) NOT NULL CHECK (status IN ('DRAFT','ORDERED','PARTIALLY_RECEIVED','RECEIVED','CANCELLED')),
    notes TEXT,
    PRIMARY KEY (id),
    CONSTRAINT uq_purchase_orders_clinic_folio UNIQUE (clinic_id, folio)
);

CREATE INDEX IF NOT EXISTS idx_purchase_orders_clinic_status
    ON core.purchase_orders (clinic_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS core.purchase_order_lines (
    line_order integer NOT NULL,
    ordered_quantity numeric(19,4) NOT NULL CHECK (ordered_quantity > 0),
    received_quantity numeric(19,4) NOT NULL DEFAULT 0 CHECK (received_quantity >= 0),
    unit_cost numeric(19,4) NOT NULL CHECK (unit_cost > 0),
    id uuid NOT NULL,
    purchase_order_id uuid NOT NULL,
    material_id uuid NOT NULL,
    material_name varchar(255) NOT NULL,
    unit_of_measure varchar(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_purchase_order_line_material UNIQUE (purchase_order_id, material_id)
);

CREATE INDEX IF NOT EXISTS idx_purchase_order_lines_order
    ON core.purchase_order_lines (purchase_order_id, line_order);

CREATE TABLE IF NOT EXISTS core.purchase_receipts (
    received_at timestamp(6) NOT NULL,
    created_at timestamp(6) NOT NULL,
    clinic_id uuid NOT NULL,
    id uuid NOT NULL,
    purchase_order_id uuid NOT NULL,
    notes TEXT,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_purchase_receipts_order
    ON core.purchase_receipts (purchase_order_id, received_at DESC);

CREATE TABLE IF NOT EXISTS core.purchase_receipt_lines (
    expiration_date date,
    quantity numeric(19,4) NOT NULL CHECK (quantity > 0),
    unit_cost numeric(19,4) NOT NULL CHECK (unit_cost > 0),
    id uuid NOT NULL,
    receipt_id uuid NOT NULL,
    purchase_order_line_id uuid NOT NULL,
    material_id uuid NOT NULL,
    inventory_movement_id uuid NOT NULL,
    material_name varchar(255) NOT NULL,
    lot_number varchar(255),
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_purchase_receipt_lines_receipt
    ON core.purchase_receipt_lines (receipt_id);
