-- V015: Datos fiscales, contacto y presencia digital de la empresa
ALTER TABLE company_management.company ADD COLUMN rfc VARCHAR(13);
ALTER TABLE company_management.company ADD COLUMN tax_regime VARCHAR(100);
ALTER TABLE company_management.company ADD COLUMN incorporation_date DATE;
ALTER TABLE company_management.company ADD COLUMN phone VARCHAR(30);
ALTER TABLE company_management.company ADD COLUMN email VARCHAR(255);
ALTER TABLE company_management.company ADD COLUMN website VARCHAR(255);
ALTER TABLE company_management.company ADD COLUMN logo_url VARCHAR(500);
ALTER TABLE company_management.company ADD COLUMN fiscal_calle VARCHAR(200);
ALTER TABLE company_management.company ADD COLUMN fiscal_numero_exterior VARCHAR(30);
ALTER TABLE company_management.company ADD COLUMN fiscal_numero_interior VARCHAR(30);
ALTER TABLE company_management.company ADD COLUMN fiscal_colonia VARCHAR(120);
ALTER TABLE company_management.company ADD COLUMN fiscal_codigo_postal VARCHAR(20);
ALTER TABLE company_management.company ADD COLUMN fiscal_municipio_alcaldia VARCHAR(120);
ALTER TABLE company_management.company ADD COLUMN fiscal_estado VARCHAR(100);
ALTER TABLE company_management.company ADD COLUMN fiscal_pais VARCHAR(100);

ALTER TABLE company_management.company
    ALTER COLUMN tax_id DROP NOT NULL;

-- Se dejan anulables para permitir migrar datos existentes; los nuevos registros son validados por el dominio.
