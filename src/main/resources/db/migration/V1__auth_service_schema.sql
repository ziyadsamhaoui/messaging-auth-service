CREATE TABLE credentials (
    id                   UUID PRIMARY KEY,
    email                VARCHAR(255) NOT NULL,
    password_hash        VARCHAR(255) NOT NULL,
    role                 VARCHAR(20) NOT NULL DEFAULT 'USER',
    failed_login_attempts INT NOT NULL DEFAULT 0,
    is_locked            BOOLEAN NOT NULL DEFAULT FALSE,
    lockout_end          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT credentials_email_unique UNIQUE (email),
    CONSTRAINT credentials_role_check CHECK (role IN ('USER', 'ADMIN'))
);

CREATE INDEX idx_credentials_email ON credentials (email);

CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID NOT NULL,
    token_hash  CHAR(64) NOT NULL,
    is_expired  BOOLEAN NOT NULL DEFAULT FALSE,
    is_revoked  BOOLEAN NOT NULL DEFAULT FALSE,
    expiry_date TIMESTAMPTZ NOT NULL,

    CONSTRAINT refresh_tokens_hash_unique UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user_id_token_hash ON refresh_tokens (user_id, token_hash);

CREATE TABLE password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID NOT NULL,
    token_hash  CHAR(64) NOT NULL,
    is_used     BOOLEAN NOT NULL DEFAULT FALSE,
    is_expired  BOOLEAN NOT NULL DEFAULT FALSE,
    expiry_date TIMESTAMPTZ NOT NULL,

    CONSTRAINT password_reset_tokens_hash_unique UNIQUE (token_hash)
);

CREATE INDEX idx_password_reset_tokens_token_hash ON password_reset_tokens (token_hash);
