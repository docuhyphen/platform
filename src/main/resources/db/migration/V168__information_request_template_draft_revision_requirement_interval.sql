ALTER TABLE information_request_template_version
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_request_template_version_revision CHECK (revision >= 0);

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
        OR NEW.revision IS DISTINCT FROM OLD.revision
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

DROP TRIGGER information_request_requirement_revision_append_only ON information_request_requirement_revision;

ALTER TABLE information_request_requirement_revision
DROP
CONSTRAINT ck_information_request_requirement_revision_interval,
    ADD CONSTRAINT ck_information_request_requirement_revision_interval CHECK (
        effective_to IS NULL OR effective_to >= effective_from
        );

UPDATE information_request_requirement_revision revision
SET effective_to = GREATEST(successor.next_effective_from, revision.effective_from) FROM (SELECT id,
             LEAD(effective_from) OVER (
                 PARTITION BY information_request_requirement_id
                 ORDER BY revision_number
                 ) AS next_effective_from
      FROM information_request_requirement_revision) successor
WHERE successor.id = revision.id
  AND successor.next_effective_from IS NOT NULL
  AND revision.effective_to IS NULL;

CREATE FUNCTION information_request_requirement_revision_close_guard()
    RETURNS TRIGGER AS
    $$
BEGIN
    IF
TG_OP = 'DELETE' THEN
        IF record_disposal_in_progress() THEN
            RETURN OLD;
END IF;
        RAISE
EXCEPTION 'information request history is append-only';
END IF;

    IF
OLD.effective_to IS NOT NULL
        OR NEW.effective_to IS NULL
        OR NEW.id IS DISTINCT FROM OLD.id
        OR NEW.information_request_requirement_id IS DISTINCT FROM OLD.information_request_requirement_id
        OR NEW.information_request_id IS DISTINCT FROM OLD.information_request_id
        OR NEW.source_template_version_id IS DISTINCT FROM OLD.source_template_version_id
        OR NEW.source_template_requirement_id IS DISTINCT FROM OLD.source_template_requirement_id
        OR NEW.source_template_binding_id IS DISTINCT FROM OLD.source_template_binding_id
        OR NEW.revision_number IS DISTINCT FROM OLD.revision_number
        OR NEW.occurrence_path IS DISTINCT FROM OLD.occurrence_path
        OR NEW.effective_from IS DISTINCT FROM OLD.effective_from
        OR NEW.configuration_hash_sha256 IS DISTINCT FROM OLD.configuration_hash_sha256
        OR NEW.optimistic_version IS DISTINCT FROM OLD.optimistic_version
        OR NEW.created_at IS DISTINCT FROM OLD.created_at
    THEN
        RAISE EXCEPTION 'information request history is append-only; a requirement revision only closes its open effective interval once';
END IF;

RETURN NEW;
END;
$$
LANGUAGE plpgsql;

CREATE TRIGGER information_request_requirement_revision_close_guard_write
    BEFORE UPDATE OR
DELETE
ON information_request_requirement_revision
    FOR EACH ROW
EXECUTE FUNCTION information_request_requirement_revision_close_guard();
