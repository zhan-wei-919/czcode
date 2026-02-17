CREATE TABLE IF NOT EXISTS project_idempotency_record (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES project(id),
  actor_user_id UUID NOT NULL,
  scope VARCHAR(64) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  request_hash VARCHAR(128) NOT NULL,
  response_json TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_project_idempotency_scope_key
  ON project_idempotency_record(project_id, actor_user_id, scope, idempotency_key);

CREATE INDEX IF NOT EXISTS idx_project_idempotency_expires_at
  ON project_idempotency_record(expires_at);

CREATE INDEX IF NOT EXISTS idx_project_idempotency_project_created_at
  ON project_idempotency_record(project_id, created_at DESC);
