-- V012: Restricciones de integridad y unicitad parcial
-- Restricción de unicidad para código de sucursal dentro de la misma empresa
ALTER TABLE company_management.branch ADD CONSTRAINT uk_branch_company_code UNIQUE (public_company_id, code);

-- Restricción de unicidad para membresía única activa por empresa y usuario
ALTER TABLE company_management.company_membership ADD CONSTRAINT uk_membership_company_user UNIQUE (public_company_id, user_id);
