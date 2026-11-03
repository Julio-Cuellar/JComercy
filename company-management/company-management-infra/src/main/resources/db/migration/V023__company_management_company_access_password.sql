-- V023: Contraseña opcional de cada persona para entrar a una empresa (ERR-011, fase B4).
-- Una fila por persona y empresa. Solo guarda la huella cifrada (bcrypt), nunca la contraseña. El contador de intentos
-- fallidos y el bloqueo viven aquí para que sobrevivan a un reinicio de la aplicación.
CREATE TABLE IF NOT EXISTS company_management.company_access_password (
    id UUID PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    public_company_id VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_company_access_password_user_company UNIQUE (user_id, public_company_id)
);
