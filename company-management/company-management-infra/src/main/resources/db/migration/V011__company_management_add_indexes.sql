-- V011: Índices de rendimiento y búsqueda por ID público y relaciones
CREATE INDEX IF NOT EXISTS idx_account_public_id ON company_management.account(public_account_id);
CREATE INDEX IF NOT EXISTS idx_company_public_id ON company_management.company(public_company_id);
CREATE INDEX IF NOT EXISTS idx_company_public_account_id ON company_management.company(public_account_id);
CREATE INDEX IF NOT EXISTS idx_branch_public_id ON company_management.branch(public_branch_id);
CREATE INDEX IF NOT EXISTS idx_branch_public_company_id ON company_management.branch(public_company_id);
CREATE INDEX IF NOT EXISTS idx_membership_company_user ON company_management.company_membership(public_company_id, user_id);
CREATE INDEX IF NOT EXISTS idx_assignment_membership_branch ON company_management.branch_assignment(public_membership_id, public_branch_id);
CREATE INDEX IF NOT EXISTS idx_outbox_status_retry ON company_management.outbox_event(status, next_retry_at);
