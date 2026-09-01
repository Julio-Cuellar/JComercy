CREATE TABLE IF NOT EXISTS core.supplier_materials (
    active boolean NOT NULL DEFAULT true,
    receipt_count integer NOT NULL DEFAULT 0 CHECK (receipt_count >= 0),
    supplier_unit_cost numeric(19,4) NOT NULL CHECK (supplier_unit_cost > 0),
    last_supplied_at timestamp(6),
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    clinic_id uuid NOT NULL,
    id uuid NOT NULL,
    supplier_id uuid NOT NULL,
    material_id uuid NOT NULL,
    material_name varchar(255) NOT NULL,
    unit_of_measure varchar(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_supplier_material UNIQUE (supplier_id, material_id)
);

CREATE INDEX IF NOT EXISTS idx_supplier_materials_supplier
    ON core.supplier_materials (clinic_id, supplier_id, active, material_name);
