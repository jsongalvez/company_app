CREATE TABLE site_hub_laptop (
    id UUID PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    paired_by UUID NOT NULL REFERENCES app_user(id),
    paired_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoked_by UUID REFERENCES app_user(id)
);

CREATE INDEX idx_site_hub_laptop_active
    ON site_hub_laptop (paired_at DESC)
    WHERE revoked_at IS NULL;

CREATE TABLE site_hub_cert (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    account_key_enc BYTEA NOT NULL,
    cert_pem TEXT,
    key_pem_enc BYTEA,
    not_before TIMESTAMPTZ,
    not_after TIMESTAMPTZ,
    issued_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
