-- V030: la administración de la empresa puede administrar las sucursales de su equipo.
-- Las rutas de asignaciones exigen assignments.read, assignments.create, assignments.update y assignments.delete, y
-- COMPANY_ADMIN no tenía ninguno. El dueño de la cuenta no pasa por aquí (su "*"). Los demás permisos se conservan tal
-- como los dejó V027.
UPDATE company_management.role
SET permissions_json = '["companies.*", "branches.*", "memberships.*", "roles.*", "inventory.*", "effective_permissions.read_own", "employees.*", "operations.*", "operational_context.select", "assignments.*"]',
    updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE public_role_id = 'rol_sys_company_admin';
