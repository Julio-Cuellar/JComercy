-- V004: Tabla company
CREATE TABLE IF NOT EXISTS company_management.company (
    id UUID PRIMARY KEY,
    public_company_id VARCHAR(64) NOT NULL UNIQUE,
    public_account_id VARCHAR(64) NOT NULL REFERENCES company_management.account(public_account_id),
    legal_name VARCHAR(255) NOT NULL,
    trade_name VARCHAR(255) NOT NULL,
    tax_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    preferences_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
