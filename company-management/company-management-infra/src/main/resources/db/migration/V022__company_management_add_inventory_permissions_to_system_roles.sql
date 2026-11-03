-- V022: los roles del sistema reciben los permisos de inventario que necesitan.
-- V009 los sembró sin ninguno, y el token de un inicio de sesión real no abría inventario.
-- El dueño de la cuenta no pasa por aquí: su permiso "*" lo da ser dueño, no un rol.
UPDATE company_management.role
SET permissions_json = '["companies.*", "branches.*", "memberships.*", "roles.*", "inventory.*"]',
    updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE public_role_id = 'rol_sys_company_admin';

UPDATE company_management.role
SET permissions_json = '["branches.read", "branches.update", "operations.*", "inventory.stock.*", "inventory.warehouses.read", "inventory.products.read"]',
    updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE public_role_id = 'rol_sys_branch_manager';

UPDATE company_management.role
SET permissions_json = '["operational_context.select", "inventory.stock.read"]',
    updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE public_role_id = 'rol_sys_staff';
