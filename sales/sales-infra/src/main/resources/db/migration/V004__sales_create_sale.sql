-- V004: las ventas con sus líneas y sus pagos.
-- La unicidad por (empresa, canal, referencia externa) protege contra registrar dos veces la misma venta de un canal
-- externo. Las ventas de mostrador no traen referencia (nula), y los nulos no chocan entre sí: es lo que se quiere aquí,
-- a diferencia del saldo de inventario (V011 de inventario), donde los nulos sí cuentan como iguales.
-- El folio es nulo hasta que la venta se completa; tampoco choca entre ventas sin folio.
CREATE TABLE sales.sale (
    id UUID PRIMARY KEY,
    company_id VARCHAR(100) NOT NULL,
    branch_id VARCHAR(100) NOT NULL,
    warehouse_id UUID NOT NULL,
    channel VARCHAR(30) NOT NULL,
    external_reference VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    folio VARCHAR(30),
    total DECIMAL(14, 2) NOT NULL,
    received DECIMAL(14, 2) NOT NULL,
    change_amount DECIMAL(14, 2) NOT NULL,
    actor_user_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    cancel_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_sale_status CHECK (status IN ('PENDING', 'CONFIRMING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_sale_total CHECK (total >= 0),
    CONSTRAINT uq_sale_external_reference UNIQUE (company_id, channel, external_reference),
    CONSTRAINT uq_sale_folio UNIQUE (company_id, branch_id, folio)
);

CREATE INDEX idx_sale_branch_created ON sales.sale (company_id, branch_id, created_at);

CREATE TABLE sales.sale_line (
    sale_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    product_id UUID NOT NULL,
    sku VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    quantity DECIMAL(14, 4) NOT NULL,
    unit_price DECIMAL(14, 2) NOT NULL,
    line_total DECIMAL(14, 2) NOT NULL,
    CONSTRAINT pk_sale_line PRIMARY KEY (sale_id, line_number),
    CONSTRAINT fk_sale_line_sale FOREIGN KEY (sale_id) REFERENCES sales.sale (id),
    CONSTRAINT ck_sale_line_quantity CHECK (quantity > 0)
);

CREATE TABLE sales.sale_payment (
    sale_id UUID NOT NULL,
    payment_number INTEGER NOT NULL,
    method VARCHAR(20) NOT NULL,
    amount DECIMAL(14, 2) NOT NULL,
    reference VARCHAR(64),
    CONSTRAINT pk_sale_payment PRIMARY KEY (sale_id, payment_number),
    CONSTRAINT fk_sale_payment_sale FOREIGN KEY (sale_id) REFERENCES sales.sale (id),
    CONSTRAINT ck_sale_payment_method CHECK (method IN ('CASH', 'CARD', 'TRANSFER')),
    CONSTRAINT ck_sale_payment_amount CHECK (amount > 0)
);
