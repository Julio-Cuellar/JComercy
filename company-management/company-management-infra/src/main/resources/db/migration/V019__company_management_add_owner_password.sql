-- V019: Credencial cifrada de la persona propietaria
ALTER TABLE company_management.owner_person ADD COLUMN password_hash VARCHAR(255);
