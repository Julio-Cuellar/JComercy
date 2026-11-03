-- V007: Tabla branch_assignment
CREATE TABLE IF NOT EXISTS company_management.branch_assignment (
    id UUID PRIMARY KEY,
    public_assignment_id VARCHAR(64) NOT NULL UNIQUE,
    public_membership_id VARCHAR(64) NOT NULL REFERENCES company_management.company_membership(public_membership_id),
    public_branch_id VARCHAR(64) NOT NULL REFERENCES company_management.branch(public_branch_id),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
