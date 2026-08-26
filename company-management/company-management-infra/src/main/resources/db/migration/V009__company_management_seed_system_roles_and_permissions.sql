-- V009: Semillas deterministas para roles del sistema
INSERT INTO company_management.role (id, public_role_id, public_company_id, name, is_system_role, status, permissions_json, created_at, updated_at, version)
VALUES
('00000000-0000-0000-0000-000000000001', 'rol_sys_account_owner', NULL, 'ACCOUNT_OWNER', TRUE, 'ACTIVE', '["*"]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('00000000-0000-0000-0000-000000000002', 'rol_sys_company_admin', NULL, 'COMPANY_ADMIN', TRUE, 'ACTIVE', '["companies.*", "branches.*", "memberships.*", "roles.*"]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('00000000-0000-0000-0000-000000000003', 'rol_sys_branch_manager', NULL, 'BRANCH_MANAGER', TRUE, 'ACTIVE', '["branches.read", "branches.update", "operations.*"]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
('00000000-0000-0000-0000-000000000004', 'rol_sys_staff', NULL, 'STAFF', TRUE, 'ACTIVE', '["operational_context.select"]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
