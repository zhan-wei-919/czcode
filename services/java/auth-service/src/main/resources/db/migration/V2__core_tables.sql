CREATE TABLE IF NOT EXISTS auth_account (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL UNIQUE,
  login_name VARCHAR(64) NOT NULL UNIQUE,
  email VARCHAR(128) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  password_algo VARCHAR(32) NOT NULL,
  status SMALLINT NOT NULL,
  failed_login_count INT NOT NULL DEFAULT 0,
  locked_until TIMESTAMPTZ NULL,
  last_login_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_auth_account_status CHECK (status IN (1, 2, 3))
);

CREATE INDEX IF NOT EXISTS idx_auth_account_status
  ON auth_account(status);

CREATE TABLE IF NOT EXISTS auth_email_code (
  id UUID PRIMARY KEY,
  email VARCHAR(128) NOT NULL,
  purpose VARCHAR(32) NOT NULL,
  code_hash VARCHAR(255) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ NULL,
  send_count INT NOT NULL DEFAULT 1,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_auth_email_code_lookup
  ON auth_email_code(email, purpose, consumed_at, created_at DESC);

CREATE TABLE IF NOT EXISTS auth_refresh_token (
  id UUID PRIMARY KEY,
  account_id UUID NOT NULL REFERENCES auth_account(id),
  token_hash VARCHAR(128) NOT NULL UNIQUE,
  client_id VARCHAR(64) NULL,
  device_info VARCHAR(255) NULL,
  ip VARCHAR(64) NULL,
  user_agent VARCHAR(255) NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_account
  ON auth_refresh_token(account_id, expires_at DESC);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_revoked
  ON auth_refresh_token(revoked_at);
