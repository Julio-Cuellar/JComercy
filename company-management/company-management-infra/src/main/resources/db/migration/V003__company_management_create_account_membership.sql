-- V003: Tabla account_membership
CREATE TABLE IF NOT EXISTS company_management.account_membership (
    id UUID PRIMARY KEY,
    public_membership_id VARCHAR(64) NOT NULL UNIQUE,
    public_account_id VARCHAR(64) NOT NULL REFERENCES company_management.account(public_account_id),
    user_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
