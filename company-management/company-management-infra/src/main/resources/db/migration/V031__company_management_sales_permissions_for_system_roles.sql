-- V031: los roles del sistema reciben los permisos de ventas (etapa S1 del plan de ventas).
-- COMPANY_ADMIN recibe sales.*; BRANCH_MANAGER vende, consulta, cancela y fija precios; STAFF vende y consulta.
-- Para vender, el módulo de ventas aparta y confirma stock en inventario con el token de quien vende, y inventario acepta
-- para eso el permiso angosto inventory.reservations.write, que no deja recibir mercancía. Por eso STAFF y BRANCH_MANAGER
-- lo reciben, y STAFF recibe además la lectura de productos y almacenes para encontrar lo que vende.
-- Los demás permisos de cada rol se conservan tal como los dejaron V026, V027 y V030. No se editan esas migraciones
-- porque ya pudieron aplicarse en otras bases. Las empresas con roles propios no cambian.
UPDATE company_management.role
SET permissions_json = '["companies.*", "branches.*", "memberships.*", "roles.*", "inventory.*", "effective_permissions.read_own", "employees.*", "operations.*", "operational_context.select", "assignments.*", "sales.*"]',
    updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE public_role_id = 'rol_sys_company_admin';

UPDATE company_management.role
SET permissions_json = '["branches.read", "branches.update", "operations.*", "inventory.stock.read", "inventory.stock.write", "inventory.warehouses.read", "inventory.products.read", "effective_permissions.read_own", "inventory.reservations.write", "sales.create", "sales.read", "sales.cancel", "sales.prices.write"]',
    updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE public_role_id = 'rol_sys_branch_manager';

UPDATE company_management.role
SET permissions_json = '["operational_context.select", "inventory.stock.read", "effective_permissions.read_own", "inventory.reservations.write", "inventory.products.read", "inventory.warehouses.read", "sales.create", "sales.read"]',
    updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE public_role_id = 'rol_sys_staff';
