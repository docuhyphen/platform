ALTER TABLE information_request_template_definition
    ADD COLUMN created_by_principal_kind VARCHAR(32),
    ADD COLUMN created_by_principal_id uuid;

UPDATE information_request_template_definition
SET created_by_principal_kind = CASE WHEN created_by_app_user_id IS NOT NULL THEN 'USER' END,
    created_by_principal_id   = created_by_app_user_id;

ALTER TABLE information_request_template_definition
DROP
COLUMN created_by_app_user_id,
    ADD CONSTRAINT ck_request_template_definition_created_principal CHECK (
        (created_by_principal_kind IS NULL AND created_by_principal_id IS NULL)
        OR (created_by_principal_kind IS NOT NULL
            AND created_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION', 'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
            AND created_by_principal_id IS NOT NULL)
    );

ALTER TABLE information_request_template_version
    ADD COLUMN created_by_principal_kind VARCHAR(32),
    ADD COLUMN created_by_principal_id uuid,
    ADD COLUMN published_by_principal_kind VARCHAR(32),
    ADD COLUMN published_by_principal_id uuid,
    ADD COLUMN retired_by_principal_kind VARCHAR(32),
    ADD COLUMN retired_by_principal_id uuid;

ALTER TABLE information_request_template_version DISABLE TRIGGER request_template_version_guard_write;

UPDATE information_request_template_version
SET created_by_principal_kind   = CASE WHEN created_by_app_user_id IS NOT NULL THEN 'USER' END,
    created_by_principal_id     = created_by_app_user_id,
    published_by_principal_kind = CASE WHEN published_by_app_user_id IS NOT NULL THEN 'USER' END,
    published_by_principal_id   = published_by_app_user_id,
    retired_by_principal_kind   = CASE WHEN retired_by_app_user_id IS NOT NULL THEN 'USER' END,
    retired_by_principal_id     = retired_by_app_user_id;

ALTER TABLE information_request_template_version
DROP
COLUMN created_by_app_user_id,
    ADD CONSTRAINT ck_request_template_version_created_principal CHECK (
        (created_by_principal_kind IS NULL AND created_by_principal_id IS NULL)
        OR (created_by_principal_kind IS NOT NULL
            AND created_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION', 'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
            AND created_by_principal_id IS NOT NULL)
    ),
    DROP
COLUMN published_by_app_user_id,
    ADD CONSTRAINT ck_request_template_version_published_principal CHECK (
        (published_by_principal_kind IS NULL AND published_by_principal_id IS NULL)
        OR (published_by_principal_kind IS NOT NULL
            AND published_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION', 'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
            AND published_by_principal_id IS NOT NULL)
    ),
    DROP
COLUMN retired_by_app_user_id,
    ADD CONSTRAINT ck_request_template_version_retired_principal CHECK (
        (retired_by_principal_kind IS NULL AND retired_by_principal_id IS NULL)
        OR (retired_by_principal_kind IS NOT NULL
            AND retired_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION', 'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
            AND retired_by_principal_id IS NOT NULL)
    );

CREATE
OR REPLACE FUNCTION request_template_version_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF
TG_OP = 'DELETE' AND record_disposal_in_progress() THEN
        RETURN OLD;
END IF;
    IF
TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'a published information request template version is immutable and cannot be removed';
END IF;
RETURN OLD;
END IF;

    IF
NEW.status = 'PUBLISHED' AND OLD.status <> 'PUBLISHED' THEN
        PERFORM request_template_state_default_attestation_policies(NEW.id);
        PERFORM
request_template_state_default_review_stage(NEW.id);
        PERFORM
request_template_publication_completeness(NEW.id, NEW.schema_version_id);
END IF;

    IF
OLD.status = 'DRAFT' THEN
        RETURN NEW;
END IF;

    IF
OLD.status = 'RETIRED' THEN
        RAISE EXCEPTION 'a retired information request template version is immutable';
END IF;

    IF
NEW.status <> 'RETIRED'
        OR NEW.id <> OLD.id
        OR NEW.template_definition_id <> OLD.template_definition_id
        OR NEW.version_number <> OLD.version_number
        OR NEW.schema_version_id IS DISTINCT FROM OLD.schema_version_id
        OR NEW.submission_mode IS DISTINCT FROM OLD.submission_mode
        OR NEW.submission_stage_ordering IS DISTINCT FROM OLD.submission_stage_ordering
        OR NEW.review_stage_ordering IS DISTINCT FROM OLD.review_stage_ordering
        OR NEW.fact_reuse_purpose_key IS DISTINCT FROM OLD.fact_reuse_purpose_key
        OR NEW.published_at IS DISTINCT FROM OLD.published_at
        OR NEW.published_by_principal_kind IS DISTINCT FROM OLD.published_by_principal_kind
        OR NEW.published_by_principal_id IS DISTINCT FROM OLD.published_by_principal_id
        OR NEW.created_at IS DISTINCT FROM OLD.created_at
        OR NEW.created_by_principal_kind IS DISTINCT FROM OLD.created_by_principal_kind
        OR NEW.created_by_principal_id IS DISTINCT FROM OLD.created_by_principal_id
    THEN
        RAISE EXCEPTION 'a published information request template version is immutable except for retirement';
END IF;

RETURN NEW;
END;
$$
LANGUAGE plpgsql;

ALTER TABLE information_request_template_version ENABLE TRIGGER request_template_version_guard_write;
