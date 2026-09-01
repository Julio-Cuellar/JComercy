-- V016: Reutiliza la estructura de domicilio en sucursales
ALTER TABLE company_management.branch ADD COLUMN domicilio_calle VARCHAR(200);
ALTER TABLE company_management.branch ADD COLUMN domicilio_numero_exterior VARCHAR(30);
ALTER TABLE company_management.branch ADD COLUMN domicilio_numero_interior VARCHAR(30);
ALTER TABLE company_management.branch ADD COLUMN domicilio_colonia VARCHAR(120);
ALTER TABLE company_management.branch ADD COLUMN domicilio_codigo_postal VARCHAR(20);
ALTER TABLE company_management.branch ADD COLUMN domicilio_municipio_alcaldia VARCHAR(120);
ALTER TABLE company_management.branch ADD COLUMN domicilio_estado VARCHAR(100);
ALTER TABLE company_management.branch ADD COLUMN domicilio_pais VARCHAR(100);

-- Se conservan las columnas de V014 para compatibilidad con datos legados.
