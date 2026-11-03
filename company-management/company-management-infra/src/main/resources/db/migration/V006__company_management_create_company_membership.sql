-- V006: Tabla company_membership
CREATE TABLE IF NOT EXISTS company_management.company_membership (
    id UUID PRIMARY KEY,
    public_membership_id VARCHAR(64) NOT NULL UNIQUE,
    public_company_id VARCHAR(64) NOT NULL REFERENCES company_management.company(public_company_id),
    user_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    roles_json TEXT NOT NULL DEFAULT '[]',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
