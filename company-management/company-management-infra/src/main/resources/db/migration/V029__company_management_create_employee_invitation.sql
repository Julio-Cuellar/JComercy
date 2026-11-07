-- V029: la invitación con la que una persona registrada por RH activa su acceso.
-- Del enlace solo se guarda la huella del código (SHA-256), nunca el código: viaja únicamente en el correo. Una persona
-- puede tener varias filas (reenvíos y cancelaciones), pero a lo más una vigente. «Vencida» no se guarda: se deduce de
-- expires_at. email_status dice si el correo salió (SENT), no salió (FAILED) o todavía no se intenta (PENDING).
CREATE TABLE company_management.employee_invitation (
    id UUID PRIMARY KEY,
    public_invitation_id VARCHAR(64) NOT NULL UNIQUE,
    public_employee_id VARCHAR(64) NOT NULL REFERENCES company_management.employee_profile(public_employee_id),
    public_company_id VARCHAR(64) NOT NULL REFERENCES company_management.company(public_company_id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    email_status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_employee_invitation_employee ON company_management.employee_invitation(public_employee_id, created_at);
