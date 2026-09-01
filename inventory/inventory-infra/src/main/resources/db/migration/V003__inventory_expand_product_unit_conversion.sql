ALTER TABLE inventory.product_unit_conversion ADD COLUMN rounding_scale INTEGER NOT NULL DEFAULT 9;
ALTER TABLE inventory.product_unit_conversion ADD COLUMN is_purchase_unit BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE inventory.product_unit_conversion ADD COLUMN is_sales_unit BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE inventory.product_unit_conversion ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE inventory.product_unit_conversion
    ADD CONSTRAINT ck_conversion_rounding_scale CHECK (rounding_scale BETWEEN 0 AND 9);
