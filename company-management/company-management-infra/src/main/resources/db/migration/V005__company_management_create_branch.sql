-- V005: Tabla branch
CREATE TABLE IF NOT EXISTS company_management.branch (
    id UUID PRIMARY KEY,
    public_branch_id VARCHAR(64) NOT NULL UNIQUE,
    public_company_id VARCHAR(64) NOT NULL REFERENCES company_management.company(public_company_id),
    code VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    is_head_office BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    time_zone VARCHAR(64) NOT NULL DEFAULT 'America/Mexico_City',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
