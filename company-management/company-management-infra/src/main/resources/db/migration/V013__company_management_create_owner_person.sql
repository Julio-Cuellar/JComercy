-- V013: Persona propietaria asociada a la cuenta administrativa
CREATE TABLE company_management.owner_person (
    id UUID PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(160) NOT NULL,
    birth_date DATE NOT NULL,
    rfc VARCHAR(13) NOT NULL,
    phone_number VARCHAR(30) NOT NULL,
    email VARCHAR(255) NOT NULL,
    state VARCHAR(100) NOT NULL,
    address VARCHAR(500) NOT NULL
);

ALTER TABLE company_management.account
    ADD COLUMN owner_person_id UUID;

-- Se conserva temporalmente la columna legado para compatibilidad con datos previos.
ALTER TABLE company_management.account
    ALTER COLUMN owner_user_id DROP NOT NULL;

ALTER TABLE company_management.account
    ADD CONSTRAINT fk_account_owner_person
    FOREIGN KEY (owner_person_id) REFERENCES company_management.owner_person(id);

CREATE UNIQUE INDEX ux_account_owner_person
    ON company_management.account(owner_person_id);
