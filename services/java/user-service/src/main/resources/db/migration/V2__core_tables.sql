CREATE TABLE IF NOT EXISTS user_profile (
  id UUID PRIMARY KEY,
  nickname VARCHAR(64) NOT NULL,
  avatar_url VARCHAR(255),
  bio VARCHAR(512),
  timezone VARCHAR(64) NOT NULL,
  locale VARCHAR(32) NOT NULL,
  status SMALLINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ NULL,
  CONSTRAINT chk_user_profile_status CHECK (status IN (1, 2))
);

CREATE INDEX IF NOT EXISTS idx_user_profile_status
  ON user_profile(status);

CREATE INDEX IF NOT EXISTS idx_user_profile_created_at
  ON user_profile(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_profile_deleted_at
  ON user_profile(deleted_at);
