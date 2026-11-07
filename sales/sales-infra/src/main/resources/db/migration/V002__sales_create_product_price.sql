-- V002: el precio de venta de cada producto de una empresa. El producto vive en inventario: aquí solo se guarda su
-- identificador público, sin llave foránea, porque pertenece a otro esquema.
CREATE TABLE sales.product_price (
    company_id VARCHAR(100) NOT NULL,
    product_id UUID NOT NULL,
    amount DECIMAL(14, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    CONSTRAINT pk_product_price PRIMARY KEY (company_id, product_id),
    CONSTRAINT ck_product_price_amount CHECK (amount >= 0)
);
