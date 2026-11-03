-- V017: Amplía el modelo de sucursal con clasificación, fiscal, contacto y operación
ALTER TABLE company_management.branch ADD COLUMN type VARCHAR(32) NOT NULL DEFAULT 'SUCURSAL';
ALTER TABLE company_management.branch ADD COLUMN email VARCHAR(255);
ALTER TABLE company_management.branch ADD COLUMN rfc VARCHAR(13);
ALTER TABLE company_management.branch ADD COLUMN fiscal_name VARCHAR(255);
ALTER TABLE company_management.branch ADD COLUMN fiscal_postal_code VARCHAR(20);
ALTER TABLE company_management.branch ADD COLUMN centro_costo_id VARCHAR(64);
ALTER TABLE company_management.branch ADD COLUMN almacen_id VARCHAR(64);
ALTER TABLE company_management.branch ADD COLUMN lista_precios_id VARCHAR(64);
ALTER TABLE company_management.branch ADD COLUMN serie_facturacion VARCHAR(32);
ALTER TABLE company_management.branch ADD COLUMN folio_actual BIGINT DEFAULT 0;
ALTER TABLE company_management.branch ADD COLUMN moneda VARCHAR(3) NOT NULL DEFAULT 'MXN';
ALTER TABLE company_management.branch ADD COLUMN responsable_id VARCHAR(64);
ALTER TABLE company_management.branch ADD COLUMN fecha_apertura DATE;
ALTER TABLE company_management.branch ADD COLUMN fecha_cierre DATE;
