CREATE TABLE IF NOT EXISTS project_branch (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES project(id),
  name VARCHAR(128) NOT NULL,
  branch_type SMALLINT NOT NULL,
  based_on_branch_id UUID NULL REFERENCES project_branch(id),
  head_checkpoint_id UUID NULL,
  status SMALLINT NOT NULL DEFAULT 1,
  created_by UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ NULL,
  CONSTRAINT chk_project_branch_type CHECK (branch_type IN (1, 2, 3, 4, 5)),
  CONSTRAINT chk_project_branch_status CHECK (status IN (1, 2, 3))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_project_branch_name_active
  ON project_branch(project_id, name)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_project_branch_project_status_updated_at
  ON project_branch(project_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_project_branch_project_type_created_at
  ON project_branch(project_id, branch_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_project_branch_based_on
  ON project_branch(project_id, based_on_branch_id);

CREATE INDEX IF NOT EXISTS idx_project_branch_deleted_at
  ON project_branch(deleted_at);

CREATE TABLE IF NOT EXISTS branch_protection_rule (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES project(id),
  branch_pattern VARCHAR(128) NOT NULL,
  min_push_role SMALLINT NOT NULL,
  min_merge_role SMALLINT NOT NULL,
  require_pr BOOLEAN NOT NULL DEFAULT FALSE,
  allow_force_push BOOLEAN NOT NULL DEFAULT FALSE,
  allow_delete_branch BOOLEAN NOT NULL DEFAULT FALSE,
  created_by UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ NULL,
  CONSTRAINT chk_branch_protection_min_push_role CHECK (min_push_role IN (1, 2, 3, 4)),
  CONSTRAINT chk_branch_protection_min_merge_role CHECK (min_merge_role IN (1, 2, 3, 4))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_branch_protection_rule_pattern_active
  ON branch_protection_rule(project_id, branch_pattern)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_branch_protection_rule_project
  ON branch_protection_rule(project_id);

CREATE INDEX IF NOT EXISTS idx_branch_protection_rule_deleted_at
  ON branch_protection_rule(deleted_at);

CREATE TABLE IF NOT EXISTS branch_checkpoint (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES project(id),
  branch_id UUID NOT NULL REFERENCES project_branch(id),
  title VARCHAR(200) NOT NULL,
  description TEXT NULL,
  snapshot_ref VARCHAR(512) NOT NULL,
  snapshot_size_bytes BIGINT NOT NULL,
  file_count INT NOT NULL,
  created_by UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ NULL,
  CONSTRAINT chk_branch_checkpoint_snapshot_size CHECK (snapshot_size_bytes >= 0),
  CONSTRAINT chk_branch_checkpoint_file_count CHECK (file_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_branch_checkpoint_branch_created_at
  ON branch_checkpoint(branch_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_branch_checkpoint_project_created_at
  ON branch_checkpoint(project_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_branch_checkpoint_deleted_at
  ON branch_checkpoint(deleted_at);

CREATE TABLE IF NOT EXISTS publish_record (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES project(id),
  source_branch_id UUID NOT NULL REFERENCES project_branch(id),
  target_branch_id UUID NOT NULL REFERENCES project_branch(id),
  source_checkpoint_id UUID NOT NULL REFERENCES branch_checkpoint(id),
  publish_status SMALLINT NOT NULL,
  conflict_summary TEXT NULL,
  published_by UUID NOT NULL,
  published_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_publish_record_status CHECK (publish_status IN (1, 2, 3)),
  CONSTRAINT chk_publish_record_branches CHECK (source_branch_id <> target_branch_id)
);

CREATE INDEX IF NOT EXISTS idx_publish_record_project_published_at
  ON publish_record(project_id, published_at DESC);

CREATE INDEX IF NOT EXISTS idx_publish_record_target_branch_published_at
  ON publish_record(target_branch_id, published_at DESC);

CREATE INDEX IF NOT EXISTS idx_publish_record_status_created_at
  ON publish_record(publish_status, created_at DESC);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'fk_project_branch_head_checkpoint'
  ) THEN
    ALTER TABLE project_branch
      ADD CONSTRAINT fk_project_branch_head_checkpoint
      FOREIGN KEY (head_checkpoint_id)
      REFERENCES branch_checkpoint(id);
  END IF;
END $$;
