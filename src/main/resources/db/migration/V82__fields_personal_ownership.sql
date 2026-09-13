-- Reusable Fields configuration can be owned by a person.
--
-- Ownership of a Field Definition, a Schema Definition, and the assignment of one was recorded as a
-- scope kind plus an organization id, so the only owners expressible were the platform and an
-- organization. A user who belongs to no organization could not hold any of the three, even though
-- personal ownership is first class for the resources those Schemas govern.
--
-- Recording the person is one half of it. The other half is the key indexes: they fold every owner
-- into COALESCE(scope_org_id, '000...0'), so two people holding the same key would meet at the same
-- sentinel and the second would be refused. Each owner now contributes its own id to that
-- expression, and the scope kind in front of it keeps the three owner spaces apart even when an
-- organization id and a user id happen to be the same value.

ALTER TABLE field_definition
    ADD COLUMN scope_user_id uuid REFERENCES app_user(id);
ALTER TABLE schema_definition
    ADD COLUMN scope_user_id uuid REFERENCES app_user(id);
ALTER TABLE schema_assignment
    ADD COLUMN scope_user_id uuid REFERENCES app_user(id);

-- ── Scope kinds ──────────────────────────────────────────────────────────────────────────────

ALTER TABLE field_definition
    DROP CONSTRAINT ck_field_def_scope_kind;
ALTER TABLE field_definition
    ADD CONSTRAINT ck_field_def_scope_kind
        CHECK (scope_kind IN ('PLATFORM', 'ORGANIZATION', 'PERSONAL'));

ALTER TABLE schema_definition
    DROP CONSTRAINT ck_schema_def_scope_kind;
ALTER TABLE schema_definition
    ADD CONSTRAINT ck_schema_def_scope_kind
        CHECK (scope_kind IN ('PLATFORM', 'ORGANIZATION', 'PERSONAL'));

ALTER TABLE schema_assignment
    DROP CONSTRAINT ck_assignment_scope_kind;
ALTER TABLE schema_assignment
    ADD CONSTRAINT ck_assignment_scope_kind
        CHECK (scope_kind IN ('PLATFORM', 'ORGANIZATION', 'PERSONAL'));

-- ── Owners ───────────────────────────────────────────────────────────────────────────────────
-- A scope kind names exactly one owner. Two owners on one row, or none where one is required,
-- leaves no answer to who governs the configuration.

ALTER TABLE field_definition
    DROP CONSTRAINT ck_field_def_scope_org;
ALTER TABLE field_definition
    ADD CONSTRAINT ck_field_def_scope_owner CHECK (
        (scope_kind = 'PLATFORM' AND scope_org_id IS NULL AND scope_user_id IS NULL) OR
        (scope_kind = 'ORGANIZATION' AND scope_org_id IS NOT NULL AND scope_user_id IS NULL) OR
        (scope_kind = 'PERSONAL' AND scope_org_id IS NULL AND scope_user_id IS NOT NULL)
    );

ALTER TABLE schema_definition
    DROP CONSTRAINT ck_schema_def_scope_org;
ALTER TABLE schema_definition
    ADD CONSTRAINT ck_schema_def_scope_owner CHECK (
        (scope_kind = 'PLATFORM' AND scope_org_id IS NULL AND scope_user_id IS NULL) OR
        (scope_kind = 'ORGANIZATION' AND scope_org_id IS NOT NULL AND scope_user_id IS NULL) OR
        (scope_kind = 'PERSONAL' AND scope_org_id IS NULL AND scope_user_id IS NOT NULL)
    );

-- An assignment copies the scope of the Schema Definition it applies, and nothing ever held it to
-- that. Any row that drifted is restored from the definition, which is the authority, before the
-- rule that would have prevented the drift is put in place.
UPDATE schema_assignment assignment
SET scope_kind = definition.scope_kind,
    scope_org_id = definition.scope_org_id,
    scope_user_id = definition.scope_user_id
FROM schema_version version
         JOIN schema_definition definition ON definition.id = version.schema_definition_id
WHERE version.id = assignment.schema_version_id
  AND (assignment.scope_kind IS DISTINCT FROM definition.scope_kind
    OR assignment.scope_org_id IS DISTINCT FROM definition.scope_org_id
    OR assignment.scope_user_id IS DISTINCT FROM definition.scope_user_id);

ALTER TABLE schema_assignment
    ADD CONSTRAINT ck_assignment_scope_owner CHECK (
        (scope_kind = 'PLATFORM' AND scope_org_id IS NULL AND scope_user_id IS NULL) OR
        (scope_kind = 'ORGANIZATION' AND scope_org_id IS NOT NULL AND scope_user_id IS NULL) OR
        (scope_kind = 'PERSONAL' AND scope_org_id IS NULL AND scope_user_id IS NOT NULL)
    );

-- ── Key uniqueness within an owner ───────────────────────────────────────────────────────────
-- A stable key belongs to one owner, so the owner is part of the key. The scope kind leads the
-- expression, so the platform sentinel and an owner whose id happens to equal it stay apart, and
-- the owner check guarantees at most one of the two owner columns contributes.

DROP INDEX ux_field_def_key;
CREATE UNIQUE INDEX ux_field_def_key
    ON field_definition (
                         scope_kind,
                         COALESCE(scope_org_id, scope_user_id, '00000000-0000-0000-0000-000000000000'),
                         namespace,
                         field_key
        );

DROP INDEX ux_schema_def_key;
CREATE UNIQUE INDEX ux_schema_def_key
    ON schema_definition (
                          scope_kind,
                          COALESCE(scope_org_id, scope_user_id, '00000000-0000-0000-0000-000000000000'),
                          namespace,
                          schema_key
        );

CREATE INDEX ix_field_def_user ON field_definition (scope_user_id);
CREATE INDEX ix_schema_def_user ON schema_definition (scope_user_id);
