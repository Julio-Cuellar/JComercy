-- V027: la administración de la empresa puede dar de alta a su equipo.
-- Dar un rol exige tener todos sus permisos (nadie da más de lo que tiene). El rol COMPANY_ADMIN no tenía los de
-- recursos humanos (employees.*) ni los de los dos roles que da a diario: STAFF (operational_context.select) y
-- BRANCH_MANAGER (operations.*). Con esto puede registrar empleados con esos roles. El dueño de la cuenta no pasa
-- por aquí (su "*"). Los demás permisos se conservan tal como los dejó V026.
UPDATE company_management.role
SET permissions_json = '["companies.*", "branches.*", "memberships.*", "roles.*", "inventory.*", "effective_permissions.read_own", "employees.*", "operations.*", "operational_context.select"]',
    updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE public_role_id = 'rol_sys_company_admin';
