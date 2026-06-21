-- V8: Add PERSONAL scope to workflow_definition
--
-- Allows individual users to own private workflow definitions that are not
-- tied to an organisation. Mirrors the PERSONAL scope already present on
-- blueprint_definition.

-- Drop the old scope constraints (exact names from V1 baseline).
ALTER TABLE workflow_definition DROP CONSTRAINT IF EXISTS workflow_definition_scope_check;
ALTER TABLE workflow_definition DROP CONSTRAINT IF EXISTS ck_workflow_def_scope;

-- Drop the global name+version uniqueness constraint; with PERSONAL scope two
-- users could legitimately have identically-named personal definitions.
ALTER TABLE workflow_definition DROP CONSTRAINT IF EXISTS uq_workflow_def_name_version;

-- Re-add scope enum check with PERSONAL included.
ALTER TABLE workflow_definition
    ADD CONSTRAINT workflow_definition_scope_check
    CHECK (scope IN ('APP', 'ORG', 'PERSONAL'));

-- Re-add structural integrity: APP → no org, ORG → org required, PERSONAL → no org.
ALTER TABLE workflow_definition
    ADD CONSTRAINT ck_workflow_def_scope CHECK (
        (scope = 'APP'      AND organization_id IS NULL)
        OR (scope = 'ORG'   AND organization_id IS NOT NULL)
        OR (scope = 'PERSONAL' AND organization_id IS NULL)
    );

-- Index to support fast lookups of a user's PERSONAL definitions.
CREATE INDEX ix_workflow_def_creator ON workflow_definition (created_by_app_user_id)
    WHERE is_deleted = false;
