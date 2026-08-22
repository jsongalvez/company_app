-- Credential tokens (#350): single-use expiring secrets for account flows where nobody
-- but the account holder may know the credential. INVITE backs the admin-minted
-- account-setup link (#346 decision 2); PASSWORD_RESET is reserved for #353.
CREATE TYPE credential_purpose AS ENUM ('INVITE', 'PASSWORD_RESET');

CREATE TABLE credential_token (
    id          uuid                PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash  varchar(64)         NOT NULL UNIQUE,
    purpose     credential_purpose  NOT NULL,
    user_id     uuid                NOT NULL REFERENCES app_user(id),
    expires_at  timestamptz         NOT NULL,
    consumed_at timestamptz,
    created_by  uuid                REFERENCES app_user(id),
    created_at  timestamptz         NOT NULL DEFAULT now()
);

CREATE INDEX idx_credential_token_user ON credential_token (user_id);
