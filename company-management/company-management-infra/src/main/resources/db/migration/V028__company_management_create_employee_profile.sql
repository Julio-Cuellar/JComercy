-- V028: el perfil de una persona de la empresa, registrado por RH antes de que tenga acceso.
-- Tiene los datos que hacen falta para crear su cuenta al activarla (nombre, RFC, fecha de nacimiento, teléfono,
-- correo, estado y domicilio) más la CURP, el rol y las sucursales que RH eligió. Pertenece a una empresa: el RFC, el
-- correo y la CURP son únicos dentro de ella, no en todo el sistema (una persona puede trabajar en dos empresas).
-- El rol y las sucursales se guardan como listas JSON de identificadores públicos.
CREATE TABLE company_management.employee_profile (
    id UUID PRIMARY KEY,
    public_employee_id VARCHAR(64) NOT NULL UNIQUE,
    public_company_id VARCHAR(64) NOT NULL REFERENCES company_management.company(public_company_id),
    profile_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(160) NOT NULL,
    birth_date DATE NOT NULL,
    rfc VARCHAR(13) NOT NULL,
    curp VARCHAR(18) NOT NULL,
    phone_number VARCHAR(30) NOT NULL,
    email VARCHAR(255) NOT NULL,
    state VARCHAR(100) NOT NULL,
    address VARCHAR(500) NOT NULL,
    role_ids_json TEXT NOT NULL,
    branch_ids_json TEXT NOT NULL,
    account_public_id VARCHAR(64),
    membership_public_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_employee_profile_company_rfc ON company_management.employee_profile(public_company_id, rfc);
CREATE UNIQUE INDEX ux_employee_profile_company_email ON company_management.employee_profile(public_company_id, email);
CREATE UNIQUE INDEX ux_employee_profile_company_curp ON company_management.employee_profile(public_company_id, curp);
CREATE INDEX ix_employee_profile_company ON company_management.employee_profile(public_company_id, created_at);
