-- V026: todo rol del sistema puede consultar sus propios permisos efectivos.
-- La ruta GET /memberships/me/effective-permissions exige effective_permissions.read_own, y ningún rol lo tenía:
-- una persona con rol STAFF no podía ver lo que puede hacer. El dueño de la cuenta no pasa por aquí (su "*").
-- Los demás permisos de cada rol se conservan tal como los dejaron V022 y V024.
UPDATE company_management.role
SET permissions_json = '["companies.*", "branches.*", "memberships.*", "roles.*", "inventory.*", "effective_permissions.read_own"]',
    updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE public_role_id = 'rol_sys_company_admin';

UPDATE company_management.role
SET permissions_json = '["branches.read", "branches.update", "operations.*", "inventory.stock.read", "inventory.stock.write", "inventory.warehouses.read", "inventory.products.read", "effective_permissions.read_own"]',
    updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE public_role_id = 'rol_sys_branch_manager';

UPDATE company_management.role
SET permissions_json = '["operational_context.select", "inventory.stock.read", "effective_permissions.read_own"]',
    updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE public_role_id = 'rol_sys_staff';
