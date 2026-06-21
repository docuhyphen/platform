-- Add publish/unpublish support to workflow definitions.
-- Org admins can see and edit all definitions (published or draft).
-- Regular org members can only see definitions where is_published = true.
ALTER TABLE workflow_definition
    ADD COLUMN is_published BOOLEAN NOT NULL DEFAULT FALSE;
