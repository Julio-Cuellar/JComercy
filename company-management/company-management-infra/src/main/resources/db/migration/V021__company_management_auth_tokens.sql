CREATE TABLE IF NOT EXISTS company_management.refresh_token (
    id UUID PRIMARY KEY, token_hash VARCHAR(64) NOT NULL UNIQUE, account_id VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL, revoked_at TIMESTAMP WITH TIME ZONE
);
CREATE TABLE IF NOT EXISTS company_management.password_reset_token (
    id UUID PRIMARY KEY, token_hash VARCHAR(64) NOT NULL UNIQUE, account_id VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL, used_at TIMESTAMP WITH TIME ZONE
);
