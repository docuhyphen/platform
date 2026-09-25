-- Authorship of a stored answer, a Schema Assignment, and a recorded revision is named through the
-- canonical principal only. The registered-user columns kept beside it could only ever repeat the
-- registered user the canonical pair already names, which their consistency rules enforced, so
-- dropping them loses nothing.

ALTER TABLE field_value
    DROP CONSTRAINT ck_field_value_principal_legacy,
    DROP COLUMN updated_by_app_user_id;

ALTER TABLE schema_assignment
    DROP CONSTRAINT ck_assignment_principal_legacy,
    DROP COLUMN assigned_by_app_user_id;

ALTER TABLE field_value_revision
    DROP CONSTRAINT ck_field_value_revision_principal_legacy,
    DROP COLUMN recorded_by_app_user_id;
