CREATE TABLE IF NOT EXISTS project_audit_log (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES project(id),
  actor_user_id UUID NOT NULL,
  action VARCHAR(64) NOT NULL,
  target_type VARCHAR(32) NULL,
  target_id UUID NULL,
  detail_json TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_project_audit_log_project_created_at
  ON project_audit_log(project_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_project_audit_log_action_created_at
  ON project_audit_log(action, created_at DESC);
