-- V024: el encargado de sucursal recibe y consulta mercancía, pero no ajusta el stock ni lo deja en negativo (parte C).
-- V022 le dio inventory.stock.*, que incluye inventory.stock.adjust e inventory.stock.negative.enable. Ahora recibe solo
-- inventory.stock.read e inventory.stock.write; cada empresa puede concederle lo demás con un rol propio.
-- No se edita V022 porque ya pudo aplicarse en otras bases. El resto del rol y los demás roles no cambian.
UPDATE company_management.role
SET permissions_json = '["branches.read", "branches.update", "operations.*", "inventory.stock.read", "inventory.stock.write", "inventory.warehouses.read", "inventory.products.read"]',
    updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE public_role_id = 'rol_sys_branch_manager';
