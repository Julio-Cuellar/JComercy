-- V008: Tablas role y permission
CREATE TABLE IF NOT EXISTS company_management.role (
    id UUID PRIMARY KEY,
    public_role_id VARCHAR(64) NOT NULL UNIQUE,
    public_company_id VARCHAR(64),
    name VARCHAR(255) NOT NULL,
    is_system_role BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    permissions_json TEXT NOT NULL DEFAULT '[]',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
