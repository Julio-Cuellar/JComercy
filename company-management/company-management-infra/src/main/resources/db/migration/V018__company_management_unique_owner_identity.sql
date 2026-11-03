-- V018: RFC y correo electrónico únicos para la persona propietaria de la cuenta
CREATE UNIQUE INDEX ux_owner_person_rfc ON company_management.owner_person(rfc);
CREATE UNIQUE INDEX ux_owner_person_email ON company_management.owner_person(email);
