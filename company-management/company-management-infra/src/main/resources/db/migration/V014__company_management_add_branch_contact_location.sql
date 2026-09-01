-- V014: Datos obligatorios de contacto y ubicación de la sucursal
ALTER TABLE company_management.branch ADD COLUMN address VARCHAR(500);
ALTER TABLE company_management.branch ADD COLUMN phone_number VARCHAR(30);
ALTER TABLE company_management.branch ADD COLUMN postal_code VARCHAR(20);
ALTER TABLE company_management.branch ADD COLUMN state VARCHAR(100);
ALTER TABLE company_management.branch ADD COLUMN city VARCHAR(100);

-- Las columnas quedan obligatorias para nuevas sucursales; los datos legados deben regularizarse antes de endurecer la restricción.
