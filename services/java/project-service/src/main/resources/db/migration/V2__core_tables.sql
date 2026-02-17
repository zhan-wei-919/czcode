CREATE TABLE IF NOT EXISTS project (
  id UUID PRIMARY KEY,
  project_key VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  description TEXT NULL,
  owner_user_id UUID NOT NULL,
  visibility SMALLINT NOT NULL,
  status SMALLINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ NULL,
  CONSTRAINT chk_project_visibility CHECK (visibility IN (1, 2, 3)),
  CONSTRAINT chk_project_status CHECK (status IN (1, 2))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_project_key_active
  ON project(project_key)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_project_owner
  ON project(owner_user_id);

CREATE INDEX IF NOT EXISTS idx_project_status_updated_at
  ON project(status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_project_deleted_at
  ON project(deleted_at);

CREATE TABLE IF NOT EXISTS project_member (
  project_id UUID NOT NULL REFERENCES project(id),
  user_id UUID NOT NULL,
  role SMALLINT NOT NULL,
  joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  inviter_user_id UUID NULL,
  status SMALLINT NOT NULL DEFAULT 1,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (project_id, user_id),
  CONSTRAINT chk_project_member_role CHECK (role IN (1, 2, 3, 4)),
  CONSTRAINT chk_project_member_status CHECK (status IN (1, 2))
);

CREATE INDEX IF NOT EXISTS idx_project_member_user_status
  ON project_member(user_id, status);

CREATE INDEX IF NOT EXISTS idx_project_member_project_role
  ON project_member(project_id, role);

CREATE TABLE IF NOT EXISTS project_file_node (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES project(id),
  parent_id UUID NULL REFERENCES project_file_node(id),
  name VARCHAR(128) NOT NULL,
  is_directory BOOLEAN NOT NULL,
  collab_doc_id UUID NULL,
  path TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ NULL,
  CONSTRAINT chk_project_file_node_doc_ref
    CHECK (
      (is_directory = TRUE AND collab_doc_id IS NULL)
      OR
      (is_directory = FALSE AND collab_doc_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_project_file_node_path_active
  ON project_file_node(project_id, path)
  WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_project_file_node_sibling_active
  ON project_file_node(
    project_id,
    COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::UUID),
    name
  )
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_project_file_node_parent
  ON project_file_node(project_id, parent_id);

CREATE INDEX IF NOT EXISTS idx_project_file_node_deleted_at
  ON project_file_node(deleted_at);
