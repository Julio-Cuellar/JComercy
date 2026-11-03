CREATE TABLE inventory.company_unit_of_measure (
    id UUID PRIMARY KEY,
    public_unit_id UUID NOT NULL UNIQUE,
    company_id VARCHAR(100) NOT NULL,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    dimension VARCHAR(30) NOT NULL,
    base_unit_code VARCHAR(20) NOT NULL,
    conversion_factor DECIMAL(24, 9) NOT NULL,
    decimals INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_company_unit_code UNIQUE (company_id, code),
    CONSTRAINT ck_company_unit_factor_positive CHECK (conversion_factor > 0),
    CONSTRAINT ck_company_unit_decimals_range CHECK (decimals BETWEEN 0 AND 9),
    CONSTRAINT ck_company_unit_dimension CHECK (dimension IN ('MASS', 'VOLUME', 'LENGTH', 'AREA', 'COUNT', 'TIME')),
    CONSTRAINT ck_company_unit_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

ALTER TABLE inventory.product DROP CONSTRAINT fk_product_stock_uom;
ALTER TABLE inventory.product DROP CONSTRAINT fk_product_purchase_uom;
ALTER TABLE inventory.product DROP CONSTRAINT fk_product_sale_uom;
ALTER TABLE inventory.product DROP CONSTRAINT fk_product_net_uom;
ALTER TABLE inventory.product_unit_conversion DROP CONSTRAINT fk_conversion_from_uom;
ALTER TABLE inventory.product_unit_conversion DROP CONSTRAINT fk_conversion_to_uom;
ALTER TABLE inventory.warehouse_location DROP CONSTRAINT fk_location_capacity_uom;
ALTER TABLE inventory.inventory_movement DROP CONSTRAINT fk_movement_unit;
