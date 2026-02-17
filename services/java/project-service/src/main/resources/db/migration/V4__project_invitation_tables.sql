CREATE TABLE IF NOT EXISTS project_invitation (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES project(id),
  invitee_email VARCHAR(128) NOT NULL,
  role SMALLINT NOT NULL,
  token VARCHAR(128) NOT NULL,
  status SMALLINT NOT NULL DEFAULT 1,
  expired_at TIMESTAMPTZ NOT NULL,
  accepted_at TIMESTAMPTZ NULL,
  created_by UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_project_invitation_role CHECK (role IN (2, 3, 4)),
  CONSTRAINT chk_project_invitation_status CHECK (status IN (1, 2, 3, 4))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_project_invitation_token
  ON project_invitation(token);

CREATE UNIQUE INDEX IF NOT EXISTS uq_project_invitation_pending_email
  ON project_invitation(project_id, invitee_email)
  WHERE status = 1;

CREATE INDEX IF NOT EXISTS idx_project_invitation_project_status_created_at
  ON project_invitation(project_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_project_invitation_project_created_at
  ON project_invitation(project_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_project_invitation_email_status
  ON project_invitation(invitee_email, status);
