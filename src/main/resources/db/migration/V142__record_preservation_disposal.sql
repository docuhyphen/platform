ALTER TABLE audit_legal_hold
    ADD COLUMN owner_kind                 VARCHAR(32),
    ADD COLUMN owner_id                   uuid,
    ADD COLUMN scope                      VARCHAR(32),
    ADD COLUMN effective_from             TIMESTAMP,
    ADD COLUMN placed_by_principal_kind   VARCHAR(32),
    ADD COLUMN placed_by_principal_id     uuid,
    ADD COLUMN released_by_principal_kind VARCHAR(32),
    ADD COLUMN released_by_principal_id   uuid,
    ADD COLUMN release_reason             VARCHAR(2048),
    ADD COLUMN hold_revision              BIGINT NOT NULL DEFAULT 1;

UPDATE audit_legal_hold
SET owner_kind                 = CASE WHEN organization_id IS NULL THEN 'PLATFORM' ELSE 'ORGANIZATION' END,
    owner_id                   = organization_id,
    scope                      = 'RESOURCE',
    effective_from             = placed_at,
    placed_by_principal_kind   = 'USER',
    placed_by_principal_id     = placed_by_user_id,
    released_by_principal_kind = CASE WHEN released_by_user_id IS NULL THEN NULL ELSE 'USER' END,
    released_by_principal_id   = released_by_user_id,
    hold_revision              = CASE WHEN status = 'RELEASED' THEN 2 ELSE 1 END;

DROP INDEX idx_audit_legal_hold_resource;

ALTER TABLE audit_legal_hold
    ALTER COLUMN owner_kind SET NOT NULL,
    ALTER COLUMN scope SET NOT NULL,
    ALTER COLUMN effective_from SET NOT NULL,
    ALTER COLUMN placed_by_principal_kind SET NOT NULL,
    ALTER COLUMN placed_by_principal_id SET NOT NULL,
    DROP COLUMN organization_id,
    DROP COLUMN placed_by_user_id,
    DROP COLUMN released_by_user_id,
    ADD CONSTRAINT ck_audit_legal_hold_owner CHECK (
        (owner_kind = 'PLATFORM' AND owner_id IS NULL) OR
        (owner_kind IN ('ORGANIZATION', 'USER') AND owner_id IS NOT NULL)
        ),
    ADD CONSTRAINT ck_audit_legal_hold_scope CHECK (scope IN ('RESOURCE', 'DESCENDANTS_AND_REFERENCES')),
    ADD CONSTRAINT ck_audit_legal_hold_resource CHECK (BTRIM(resource_type) <> '' AND BTRIM(resource_id) <> ''),
    ADD CONSTRAINT ck_audit_legal_hold_principal CHECK (
        placed_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION', 'APPLICATION',
                                     'SERVICE_ACCOUNT', 'PUBLIC_LINK') AND
        (released_by_principal_kind IS NULL OR
         released_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION', 'APPLICATION',
                                        'SERVICE_ACCOUNT', 'PUBLIC_LINK'))
        ),
    ADD CONSTRAINT ck_audit_legal_hold_release CHECK (
        (status = 'ACTIVE' AND released_at IS NULL AND released_by_principal_kind IS NULL AND
         released_by_principal_id IS NULL AND release_reason IS NULL) OR
        (status = 'RELEASED' AND released_at IS NOT NULL AND released_by_principal_kind IS NOT NULL AND
         released_by_principal_id IS NOT NULL)
        ),
    ADD CONSTRAINT ck_audit_legal_hold_revision CHECK (hold_revision >= 1);

CREATE INDEX idx_audit_legal_hold_resource ON audit_legal_hold (resource_type, resource_id, status);

CREATE INDEX idx_audit_legal_hold_owner ON audit_legal_hold (owner_kind, owner_id, status);

CREATE TABLE audit_legal_hold_event
(
    id             uuid          NOT NULL,
    hold_id        uuid          NOT NULL,
    event_number   INTEGER       NOT NULL,
    event_kind     VARCHAR(32)   NOT NULL,
    scope          VARCHAR(32)   NOT NULL,
    reason         VARCHAR(2048) NOT NULL,
    principal_kind VARCHAR(32)   NOT NULL,
    principal_id   uuid          NOT NULL,
    occurred_at    TIMESTAMP     NOT NULL,
    CONSTRAINT audit_legal_hold_event_pkey PRIMARY KEY (id),
    CONSTRAINT fk_audit_legal_hold_event_hold FOREIGN KEY (hold_id) REFERENCES audit_legal_hold (id),
    CONSTRAINT ux_audit_legal_hold_event_number UNIQUE (hold_id, event_number),
    CONSTRAINT ck_audit_legal_hold_event_kind CHECK (event_kind IN ('PLACED', 'SCOPE_CHANGED', 'RELEASED')),
    CONSTRAINT ck_audit_legal_hold_event_scope CHECK (scope IN ('RESOURCE', 'DESCENDANTS_AND_REFERENCES')),
    CONSTRAINT ck_audit_legal_hold_event_number CHECK (event_number >= 1),
    CONSTRAINT ck_audit_legal_hold_event_reason CHECK (BTRIM(reason) <> ''),
    CONSTRAINT ck_audit_legal_hold_event_principal CHECK (
        principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION', 'APPLICATION',
                           'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

INSERT INTO audit_legal_hold_event (id, hold_id, event_number, event_kind, scope, reason, principal_kind, principal_id,
                                    occurred_at)
SELECT gen_random_uuid(), id, 1, 'PLACED', scope, reason, placed_by_principal_kind, placed_by_principal_id, placed_at
FROM audit_legal_hold;

INSERT INTO audit_legal_hold_event (id, hold_id, event_number, event_kind, scope, reason, principal_kind, principal_id,
                                    occurred_at)
SELECT gen_random_uuid(), id, 2, 'RELEASED', scope, reason, released_by_principal_kind, released_by_principal_id,
       released_at
FROM audit_legal_hold
WHERE status = 'RELEASED';

CREATE FUNCTION record_preservation_append_only_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' AND record_disposal_in_progress() THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'record preservation history is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_legal_hold_event_append_only
    BEFORE UPDATE OR DELETE
    ON audit_legal_hold_event
    FOR EACH ROW
EXECUTE FUNCTION record_preservation_append_only_guard();

CREATE TABLE record_retention_schedule
(
    id                         uuid        NOT NULL,
    owner_kind                 VARCHAR(32) NOT NULL,
    owner_id                   uuid        NOT NULL,
    resource_type              VARCHAR(64) NOT NULL,
    version_number             INTEGER     NOT NULL,
    minimum_retention_days     INTEGER     NOT NULL,
    disposal_after_days        INTEGER,
    recorded_by_principal_kind VARCHAR(32) NOT NULL,
    recorded_by_principal_id   uuid        NOT NULL,
    recorded_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT record_retention_schedule_pkey PRIMARY KEY (id),
    CONSTRAINT ux_record_retention_schedule_version UNIQUE (owner_kind, owner_id, resource_type, version_number),
    CONSTRAINT ck_record_retention_schedule_owner CHECK (owner_kind IN ('ORGANIZATION', 'USER')),
    CONSTRAINT ck_record_retention_schedule_resource CHECK (resource_type IN ('INFORMATION_REQUEST')),
    CONSTRAINT ck_record_retention_schedule_version CHECK (version_number >= 1),
    CONSTRAINT ck_record_retention_schedule_days CHECK (
        minimum_retention_days >= 0 AND
        (disposal_after_days IS NULL OR disposal_after_days >= minimum_retention_days)
        ),
    CONSTRAINT ck_record_retention_schedule_principal CHECK (
        recorded_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION', 'APPLICATION',
                                       'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE TRIGGER record_retention_schedule_append_only
    BEFORE UPDATE OR DELETE
    ON record_retention_schedule
    FOR EACH ROW
EXECUTE FUNCTION record_preservation_append_only_guard();

CREATE TABLE record_disposal_claim
(
    id                        uuid        NOT NULL,
    resource_type             VARCHAR(64) NOT NULL,
    resource_id               uuid        NOT NULL,
    owner_kind                VARCHAR(32) NOT NULL,
    owner_id                  uuid        NOT NULL,
    basis                     VARCHAR(32) NOT NULL,
    retention_schedule_id     uuid,
    privacy_request_id        uuid,
    state                     VARCHAR(32) NOT NULL,
    claimed_by_principal_kind VARCHAR(32) NOT NULL,
    claimed_by_principal_id   uuid        NOT NULL,
    claimed_at                TIMESTAMPTZ NOT NULL,
    objects_deleted_at        TIMESTAMPTZ,
    finalized_at              TIMESTAMPTZ,
    attempt_count             INTEGER     NOT NULL DEFAULT 0,
    last_error_code           VARCHAR(128),
    claim_revision            BIGINT      NOT NULL DEFAULT 1,
    CONSTRAINT record_disposal_claim_pkey PRIMARY KEY (id),
    CONSTRAINT fk_record_disposal_claim_schedule FOREIGN KEY (retention_schedule_id)
        REFERENCES record_retention_schedule (id),
    CONSTRAINT ux_record_disposal_claim_resource UNIQUE (resource_type, resource_id),
    CONSTRAINT ck_record_disposal_claim_resource CHECK (resource_type IN ('INFORMATION_REQUEST')),
    CONSTRAINT ck_record_disposal_claim_owner CHECK (owner_kind IN ('ORGANIZATION', 'USER')),
    CONSTRAINT ck_record_disposal_claim_basis CHECK (
        (basis = 'RETENTION_SCHEDULE' AND retention_schedule_id IS NOT NULL AND privacy_request_id IS NULL) OR
        (basis = 'PRIVACY_DELETION' AND privacy_request_id IS NOT NULL)
        ),
    CONSTRAINT ck_record_disposal_claim_state CHECK (
        (state = 'CLAIMED' AND objects_deleted_at IS NULL AND finalized_at IS NULL) OR
        (state = 'OBJECTS_DELETED' AND objects_deleted_at IS NOT NULL AND finalized_at IS NULL) OR
        (state = 'FINALIZED' AND objects_deleted_at IS NOT NULL AND finalized_at IS NOT NULL)
        ),
    CONSTRAINT ck_record_disposal_claim_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_record_disposal_claim_revision CHECK (claim_revision >= 1),
    CONSTRAINT ck_record_disposal_claim_principal CHECK (
        claimed_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION', 'APPLICATION',
                                      'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE INDEX ix_record_disposal_claim_open ON record_disposal_claim (state, claimed_at) WHERE state <> 'FINALIZED';

CREATE FUNCTION record_disposal_in_progress()
    RETURNS BOOLEAN AS
$$
DECLARE
    claim_reference TEXT := current_setting('docuhyphen.record_disposal_claim', true);
BEGIN
    IF claim_reference IS NULL OR claim_reference = '' THEN
        RETURN FALSE;
    END IF;
    RETURN EXISTS (SELECT 1
                   FROM record_disposal_claim claim
                   WHERE claim.id = claim_reference::uuid
                     AND claim.state = 'OBJECTS_DELETED');
END;
$$ LANGUAGE plpgsql STABLE;

CREATE FUNCTION record_disposal_claim_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'a disposal claim is never deleted';
    END IF;
    IF NEW.id <> OLD.id
        OR NEW.resource_type <> OLD.resource_type
        OR NEW.resource_id <> OLD.resource_id
        OR NEW.owner_kind <> OLD.owner_kind
        OR NEW.owner_id <> OLD.owner_id
        OR NEW.basis <> OLD.basis
        OR NEW.retention_schedule_id IS DISTINCT FROM OLD.retention_schedule_id
        OR NEW.privacy_request_id IS DISTINCT FROM OLD.privacy_request_id
        OR NEW.claimed_by_principal_kind <> OLD.claimed_by_principal_kind
        OR NEW.claimed_by_principal_id <> OLD.claimed_by_principal_id
        OR NEW.claimed_at <> OLD.claimed_at
    THEN
        RAISE EXCEPTION 'a disposal claim keeps the identity it was claimed with';
    END IF;
    IF OLD.state = 'FINALIZED' THEN
        RAISE EXCEPTION 'a finalized disposal claim is immutable';
    END IF;
    IF NOT ((OLD.state = NEW.state) OR
            (OLD.state = 'CLAIMED' AND NEW.state = 'OBJECTS_DELETED') OR
            (OLD.state = 'OBJECTS_DELETED' AND NEW.state = 'FINALIZED'))
    THEN
        RAISE EXCEPTION 'a disposal claim moves from % to % only forward', OLD.state, NEW.state;
    END IF;
    IF NEW.claim_revision <> OLD.claim_revision + 1 OR NEW.attempt_count < OLD.attempt_count THEN
        RAISE EXCEPTION 'a disposal claim revision advances by one and its attempts never decrease';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER record_disposal_claim_guard_write
    BEFORE UPDATE OR DELETE
    ON record_disposal_claim
    FOR EACH ROW
EXECUTE FUNCTION record_disposal_claim_guard();

CREATE TABLE record_disposal_claim_scope
(
    claim_id      uuid         NOT NULL,
    resource_type VARCHAR(64)  NOT NULL,
    resource_id   VARCHAR(128) NOT NULL,
    direct        BOOLEAN      NOT NULL,
    CONSTRAINT record_disposal_claim_scope_pkey PRIMARY KEY (claim_id, resource_type, resource_id),
    CONSTRAINT fk_record_disposal_claim_scope_claim FOREIGN KEY (claim_id) REFERENCES record_disposal_claim (id),
    CONSTRAINT ck_record_disposal_claim_scope_key CHECK (BTRIM(resource_type) <> '' AND BTRIM(resource_id) <> '')
);

CREATE INDEX ix_record_disposal_claim_scope_key ON record_disposal_claim_scope (resource_type, resource_id);

CREATE FUNCTION record_disposal_scope_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION 'record preservation history is append-only';
    END IF;
    PERFORM pg_advisory_xact_lock(hashtext('record_preservation'));
    IF EXISTS (SELECT 1
               FROM audit_legal_hold hold
                        JOIN record_disposal_claim claim ON claim.id = NEW.claim_id
               WHERE hold.status = 'ACTIVE'
                 AND hold.resource_type = NEW.resource_type
                 AND hold.resource_id = NEW.resource_id
                 AND (NEW.direct OR hold.scope = 'DESCENDANTS_AND_REFERENCES')
                 AND (hold.owner_kind = 'PLATFORM' OR
                      (hold.owner_kind = claim.owner_kind AND hold.owner_id = claim.owner_id)))
    THEN
        RAISE EXCEPTION 'a held record cannot be claimed for disposal';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER record_disposal_claim_scope_guard_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON record_disposal_claim_scope
    FOR EACH ROW
EXECUTE FUNCTION record_disposal_scope_guard();

CREATE FUNCTION audit_legal_hold_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'a record preservation hold is never deleted';
    END IF;
    PERFORM pg_advisory_xact_lock(hashtext('record_preservation'));
    IF TG_OP = 'UPDATE' THEN
        IF OLD.status = 'RELEASED' THEN
            RAISE EXCEPTION 'a released record preservation hold is immutable';
        END IF;
        IF NEW.id <> OLD.id
            OR NEW.owner_kind <> OLD.owner_kind
            OR NEW.owner_id IS DISTINCT FROM OLD.owner_id
            OR NEW.resource_type <> OLD.resource_type
            OR NEW.resource_id <> OLD.resource_id
            OR NEW.reason <> OLD.reason
            OR NEW.case_reference IS DISTINCT FROM OLD.case_reference
            OR NEW.effective_from <> OLD.effective_from
            OR NEW.placed_by_principal_kind <> OLD.placed_by_principal_kind
            OR NEW.placed_by_principal_id <> OLD.placed_by_principal_id
            OR NEW.placed_at <> OLD.placed_at
            OR NEW.created_at <> OLD.created_at
        THEN
            RAISE EXCEPTION 'a record preservation hold keeps the identity it was placed with';
        END IF;
        IF NEW.hold_revision <> OLD.hold_revision + 1 THEN
            RAISE EXCEPTION 'a record preservation hold revision advances by one';
        END IF;
    END IF;
    IF NEW.status = 'ACTIVE' AND EXISTS (SELECT 1
                                         FROM record_disposal_claim_scope scope_key
                                                  JOIN record_disposal_claim claim ON claim.id = scope_key.claim_id
                                         WHERE claim.state <> 'FINALIZED'
                                           AND scope_key.resource_type = NEW.resource_type
                                           AND scope_key.resource_id = NEW.resource_id
                                           AND (scope_key.direct OR NEW.scope = 'DESCENDANTS_AND_REFERENCES')
                                           AND (NEW.owner_kind = 'PLATFORM' OR
                                                (claim.owner_kind = NEW.owner_kind AND claim.owner_id = NEW.owner_id)))
    THEN
        RAISE EXCEPTION 'a record under disposal cannot be placed on hold';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_legal_hold_guard_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON audit_legal_hold
    FOR EACH ROW
EXECUTE FUNCTION audit_legal_hold_guard();

CREATE TABLE record_disposal_object
(
    id                   uuid          NOT NULL,
    claim_id             uuid          NOT NULL,
    object_kind          VARCHAR(32)   NOT NULL,
    document_id          uuid          NOT NULL,
    document_version_id  uuid          NOT NULL,
    storage_provider     VARCHAR(32),
    storage_locator_kind VARCHAR(32),
    storage_locator      VARCHAR(1024),
    retained             BOOLEAN       NOT NULL,
    retained_reason      VARCHAR(64),
    deletion_outcome     VARCHAR(16),
    deleted_at           TIMESTAMPTZ,
    CONSTRAINT record_disposal_object_pkey PRIMARY KEY (id),
    CONSTRAINT fk_record_disposal_object_claim FOREIGN KEY (claim_id) REFERENCES record_disposal_claim (id),
    CONSTRAINT ux_record_disposal_object_version UNIQUE (claim_id, document_version_id),
    CONSTRAINT ck_record_disposal_object_kind CHECK (object_kind IN ('DOCUMENT_VERSION')),
    CONSTRAINT ck_record_disposal_object_locator CHECK (
        (storage_provider IS NULL AND storage_locator_kind IS NULL AND storage_locator IS NULL) OR
        (storage_provider IS NOT NULL AND storage_locator_kind IS NOT NULL AND storage_locator IS NOT NULL)
        ),
    CONSTRAINT ck_record_disposal_object_retention CHECK (
        (retained AND retained_reason IS NOT NULL AND deletion_outcome IS NULL AND deleted_at IS NULL) OR
        (NOT retained AND retained_reason IS NULL AND (deletion_outcome IS NULL) = (deleted_at IS NULL))
        ),
    CONSTRAINT ck_record_disposal_object_outcome CHECK (deletion_outcome IS NULL OR deletion_outcome IN ('DELETED', 'ABSENT'))
);

CREATE INDEX ix_record_disposal_object_version ON record_disposal_object (document_version_id);

CREATE INDEX ix_record_disposal_object_document ON record_disposal_object (document_id);

CREATE FUNCTION record_disposal_object_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'a disposal object record is never deleted';
    END IF;
    IF NEW.id <> OLD.id
        OR NEW.claim_id <> OLD.claim_id
        OR NEW.object_kind <> OLD.object_kind
        OR NEW.document_id <> OLD.document_id
        OR NEW.document_version_id <> OLD.document_version_id
        OR NEW.storage_provider IS DISTINCT FROM OLD.storage_provider
        OR NEW.storage_locator_kind IS DISTINCT FROM OLD.storage_locator_kind
        OR NEW.storage_locator IS DISTINCT FROM OLD.storage_locator
        OR NEW.retained <> OLD.retained
        OR NEW.retained_reason IS DISTINCT FROM OLD.retained_reason
        OR OLD.deleted_at IS NOT NULL
    THEN
        RAISE EXCEPTION 'a disposal object records its deletion once and nothing else';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER record_disposal_object_guard_write
    BEFORE UPDATE OR DELETE
    ON record_disposal_object
    FOR EACH ROW
EXECUTE FUNCTION record_disposal_object_guard();

CREATE TABLE record_disposal_tombstone
(
    id                    uuid        NOT NULL,
    claim_id              uuid        NOT NULL,
    resource_type         VARCHAR(64) NOT NULL,
    resource_id           uuid        NOT NULL,
    owner_kind            VARCHAR(32) NOT NULL,
    owner_id              uuid        NOT NULL,
    basis                 VARCHAR(32) NOT NULL,
    removed_rows_json     TEXT        NOT NULL,
    deleted_object_count  INTEGER     NOT NULL,
    retained_object_count INTEGER     NOT NULL,
    disposed_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT record_disposal_tombstone_pkey PRIMARY KEY (id),
    CONSTRAINT fk_record_disposal_tombstone_claim FOREIGN KEY (claim_id) REFERENCES record_disposal_claim (id),
    CONSTRAINT ux_record_disposal_tombstone_claim UNIQUE (claim_id),
    CONSTRAINT ux_record_disposal_tombstone_resource UNIQUE (resource_type, resource_id),
    CONSTRAINT ck_record_disposal_tombstone_counts CHECK (deleted_object_count >= 0 AND retained_object_count >= 0)
);

CREATE TRIGGER record_disposal_tombstone_append_only
    BEFORE UPDATE OR DELETE
    ON record_disposal_tombstone
    FOR EACH ROW
EXECUTE FUNCTION record_preservation_append_only_guard();

CREATE TABLE information_request_record_export
(
    id                          uuid         NOT NULL,
    export_kind                 VARCHAR(32)  NOT NULL,
    information_request_id      uuid,
    subject_identity_ref_id     uuid,
    owner_kind                  VARCHAR(32)  NOT NULL,
    owner_id                    uuid         NOT NULL,
    schema_version              INTEGER      NOT NULL,
    content_json                TEXT         NOT NULL,
    content_hash_algorithm      VARCHAR(16)  NOT NULL,
    content_hash                VARCHAR(64)  NOT NULL,
    content_length              BIGINT       NOT NULL,
    storage_location            VARCHAR(64)  NOT NULL,
    transfer_region             VARCHAR(64),
    transfer_decision           VARCHAR(32)  NOT NULL,
    requested_by_principal_kind VARCHAR(32)  NOT NULL,
    requested_by_principal_id   uuid         NOT NULL,
    requested_at                TIMESTAMPTZ  NOT NULL,
    CONSTRAINT information_request_record_export_pkey PRIMARY KEY (id),
    CONSTRAINT fk_information_request_record_export_request FOREIGN KEY (information_request_id)
        REFERENCES information_request (id),
    CONSTRAINT fk_information_request_record_export_subject FOREIGN KEY (subject_identity_ref_id)
        REFERENCES subject_identity_ref (id),
    CONSTRAINT ck_information_request_record_export_kind CHECK (
        (export_kind = 'REQUEST_RECORD' AND information_request_id IS NOT NULL AND subject_identity_ref_id IS NULL) OR
        (export_kind = 'SUBJECT_RECORD' AND information_request_id IS NULL AND subject_identity_ref_id IS NOT NULL)
        ),
    CONSTRAINT ck_information_request_record_export_owner CHECK (owner_kind IN ('ORGANIZATION', 'USER')),
    CONSTRAINT ck_information_request_record_export_hash CHECK (
        content_hash_algorithm = 'SHA_256' AND content_hash ~ '^[0-9a-f]{64}$'
        ),
    CONSTRAINT ck_information_request_record_export_length CHECK (content_length >= 0),
    CONSTRAINT ck_information_request_record_export_schema CHECK (schema_version >= 1),
    CONSTRAINT ck_information_request_record_export_location CHECK (BTRIM(storage_location) <> ''),
    CONSTRAINT ck_information_request_record_export_transfer CHECK (
        (transfer_decision = 'NOT_REQUESTED' AND transfer_region IS NULL) OR
        (transfer_decision = 'PERMITTED' AND transfer_region IS NOT NULL AND BTRIM(transfer_region) <> '')
        ),
    CONSTRAINT ck_information_request_record_export_principal CHECK (
        requested_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION', 'APPLICATION',
                                        'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE INDEX ix_information_request_record_export_request
    ON information_request_record_export (information_request_id, requested_at);

CREATE INDEX ix_information_request_record_export_subject
    ON information_request_record_export (subject_identity_ref_id, requested_at);

CREATE TRIGGER information_request_record_export_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_record_export
    FOR EACH ROW
EXECUTE FUNCTION record_preservation_append_only_guard();

CREATE TABLE information_request_record_export_source
(
    export_id              uuid NOT NULL,
    information_request_id uuid NOT NULL,
    CONSTRAINT information_request_record_export_source_pkey PRIMARY KEY (export_id, information_request_id),
    CONSTRAINT fk_information_request_record_export_source_export FOREIGN KEY (export_id)
        REFERENCES information_request_record_export (id),
    CONSTRAINT fk_information_request_record_export_source_request FOREIGN KEY (information_request_id)
        REFERENCES information_request (id)
);

CREATE INDEX ix_information_request_record_export_source_request
    ON information_request_record_export_source (information_request_id);

CREATE TRIGGER information_request_record_export_source_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_record_export_source
    FOR EACH ROW
EXECUTE FUNCTION record_preservation_append_only_guard();

CREATE FUNCTION record_disposal_reference_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    candidate uuid := (to_jsonb(NEW) ->> TG_ARGV[1])::uuid;
BEGIN
    IF candidate IS NULL THEN
        RETURN NEW;
    END IF;
    IF (TG_ARGV[0] = 'DOCUMENT_VERSION' AND EXISTS (SELECT 1
                                                    FROM record_disposal_object object
                                                             JOIN record_disposal_claim claim ON claim.id = object.claim_id
                                                    WHERE claim.state <> 'FINALIZED'
                                                      AND NOT object.retained
                                                      AND object.document_version_id = candidate))
        OR (TG_ARGV[0] = 'DOCUMENT' AND EXISTS (SELECT 1
                                               FROM record_disposal_object object
                                                        JOIN record_disposal_claim claim ON claim.id = object.claim_id
                                               WHERE claim.state <> 'FINALIZED'
                                                 AND NOT object.retained
                                                 AND object.document_id = candidate))
        OR (TG_ARGV[0] = 'INFORMATION_REQUEST' AND EXISTS (SELECT 1
                                                          FROM record_disposal_claim claim
                                                          WHERE claim.state <> 'FINALIZED'
                                                            AND claim.resource_type = 'INFORMATION_REQUEST'
                                                            AND claim.resource_id = candidate))
        OR (TG_ARGV[0] = 'SUBMISSION_ITEM' AND EXISTS (SELECT 1
                                                      FROM information_request_submission_item item
                                                               JOIN record_disposal_claim claim
                                                                    ON claim.resource_id = item.information_request_id
                                                      WHERE item.id = candidate
                                                        AND claim.state <> 'FINALIZED'
                                                        AND claim.resource_type = 'INFORMATION_REQUEST'))
        OR (TG_ARGV[0] = 'ACCEPTED_FACT' AND EXISTS (SELECT 1
                                                    FROM information_request_accepted_fact fact
                                                             JOIN record_disposal_claim claim
                                                                  ON claim.resource_id = fact.source_information_request_id
                                                    WHERE fact.id = candidate
                                                      AND claim.state <> 'FINALIZED'
                                                      AND claim.resource_type = 'INFORMATION_REQUEST'))
        OR (TG_ARGV[0] = 'EVIDENCE_ASSESSMENT' AND EXISTS (SELECT 1
                                                          FROM information_request_evidence_assessment assessment
                                                                   JOIN record_disposal_claim claim
                                                                        ON claim.resource_id = assessment.information_request_id
                                                          WHERE assessment.id = candidate
                                                            AND claim.state <> 'FINALIZED'
                                                            AND claim.resource_type = 'INFORMATION_REQUEST'))
    THEN
        RAISE EXCEPTION 'a record under disposal cannot gain a reference';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER record_disposal_reference_evidence_version
    BEFORE INSERT OR UPDATE
    ON information_request_evidence_version
    FOR EACH ROW
EXECUTE FUNCTION record_disposal_reference_guard('DOCUMENT_VERSION', 'document_version_id');

CREATE TRIGGER record_disposal_reference_submission_evidence
    BEFORE INSERT OR UPDATE
    ON information_request_submission_evidence
    FOR EACH ROW
EXECUTE FUNCTION record_disposal_reference_guard('DOCUMENT_VERSION', 'document_version_id');

CREATE TRIGGER record_disposal_reference_document_version
    BEFORE INSERT OR UPDATE
    ON document_version
    FOR EACH ROW
EXECUTE FUNCTION record_disposal_reference_guard('DOCUMENT', 'document_id');

CREATE TRIGGER record_disposal_reference_exchange_document
    BEFORE INSERT OR UPDATE
    ON exchange_document
    FOR EACH ROW
EXECUTE FUNCTION record_disposal_reference_guard('DOCUMENT', 'documents_id');

CREATE TRIGGER record_disposal_reference_comment_document
    BEFORE INSERT OR UPDATE
    ON document_comment
    FOR EACH ROW
EXECUTE FUNCTION record_disposal_reference_guard('DOCUMENT', 'document_id');

CREATE TRIGGER record_disposal_reference_comment_version
    BEFORE INSERT OR UPDATE
    ON document_comment
    FOR EACH ROW
EXECUTE FUNCTION record_disposal_reference_guard('DOCUMENT_VERSION', 'document_version_id');

CREATE TRIGGER record_disposal_reference_lineage
    BEFORE INSERT
    ON information_request_lineage
    FOR EACH ROW
EXECUTE FUNCTION record_disposal_reference_guard('INFORMATION_REQUEST', 'source_request_id');

CREATE TRIGGER record_disposal_reference_superseded_request
    BEFORE INSERT OR UPDATE
    ON information_request
    FOR EACH ROW
EXECUTE FUNCTION record_disposal_reference_guard('INFORMATION_REQUEST', 'superseded_by_request_id');

CREATE TRIGGER record_disposal_reference_account_link
    BEFORE INSERT OR UPDATE
    ON participant_account_link
    FOR EACH ROW
EXECUTE FUNCTION record_disposal_reference_guard('INFORMATION_REQUEST', 'linked_via_information_request_id');

CREATE TRIGGER record_disposal_reference_carry_forward
    BEFORE INSERT
    ON information_request_carry_forward
    FOR EACH ROW
EXECUTE FUNCTION record_disposal_reference_guard('SUBMISSION_ITEM', 'source_item_id');

CREATE TRIGGER record_disposal_reference_fact_supersession
    BEFORE INSERT
    ON information_request_accepted_fact
    FOR EACH ROW
EXECUTE FUNCTION record_disposal_reference_guard('ACCEPTED_FACT', 'supersedes_fact_id');

CREATE TRIGGER record_disposal_reference_fact_conflict
    BEFORE INSERT
    ON information_request_accepted_fact
    FOR EACH ROW
EXECUTE FUNCTION record_disposal_reference_guard('ACCEPTED_FACT', 'conflicting_fact_id');

CREATE TRIGGER record_disposal_reference_assessment_reuse
    BEFORE INSERT
    ON information_request_evidence_assessment
    FOR EACH ROW
EXECUTE FUNCTION record_disposal_reference_guard('EVIDENCE_ASSESSMENT', 'reused_assessment_id');

CREATE TRIGGER record_disposal_reference_record_export
    BEFORE INSERT
    ON information_request_record_export
    FOR EACH ROW
EXECUTE FUNCTION record_disposal_reference_guard('INFORMATION_REQUEST', 'information_request_id');

CREATE TRIGGER record_disposal_reference_record_export_source
    BEFORE INSERT
    ON information_request_record_export_source
    FOR EACH ROW
EXECUTE FUNCTION record_disposal_reference_guard('INFORMATION_REQUEST', 'information_request_id');

CREATE OR REPLACE FUNCTION information_request_append_only_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' AND record_disposal_in_progress() THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'information request history is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION information_request_clock_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' AND record_disposal_in_progress() THEN
        RETURN OLD;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'information request history is append-only';
    END IF;
    IF NEW.information_request_id IS DISTINCT FROM OLD.information_request_id OR
       NEW.clock_key IS DISTINCT FROM OLD.clock_key OR
       NEW.policy_version_id IS DISTINCT FROM OLD.policy_version_id OR
       NEW.urgency IS DISTINCT FROM OLD.urgency OR
       NEW.received_at IS DISTINCT FROM OLD.received_at OR
       NEW.started_by_principal_kind IS DISTINCT FROM OLD.started_by_principal_kind OR
       NEW.started_by_principal_id IS DISTINCT FROM OLD.started_by_principal_id OR
       NEW.started_at IS DISTINCT FROM OLD.started_at THEN
        RAISE EXCEPTION 'a request clock keeps its frozen inputs';
    END IF;
    IF NEW.clock_revision < OLD.clock_revision THEN
        RAISE EXCEPTION 'a request clock revision never moves backwards';
    END IF;
    IF NEW.due_cycle < OLD.due_cycle OR (OLD.state = 'STOPPED' AND NEW.state <> 'STOPPED') THEN
        RAISE EXCEPTION 'a stopped request clock stays stopped and its due cycle only advances';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION information_request_correction_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' AND record_disposal_in_progress() THEN
        RETURN OLD;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'information request history is append-only';
    END IF;

    IF TG_OP = 'INSERT' THEN
        IF NOT EXISTS (SELECT 1
                       FROM information_request_review review
                       WHERE review.id = NEW.review_id
                         AND review.package_id = NEW.package_id
                         AND review.state = 'CHANGES_REQUESTED')
        THEN
            RAISE EXCEPTION 'a correction follows a review that requested changes';
        END IF;
        RETURN NEW;
    END IF;

    IF OLD.state <> 'OPEN' THEN
        RAISE EXCEPTION 'a closed correction is immutable';
    END IF;

    IF NEW.id <> OLD.id
        OR NEW.information_request_id <> OLD.information_request_id
        OR NEW.review_id <> OLD.review_id
        OR NEW.package_id <> OLD.package_id
        OR NEW.opened_at <> OLD.opened_at
    THEN
        RAISE EXCEPTION 'a correction keeps the identity it opened with';
    END IF;

    IF NEW.resubmitted_package_id IS NOT NULL AND NOT EXISTS (SELECT 1
                                                              FROM information_request_submission_package resubmitted
                                                              WHERE resubmitted.id = NEW.resubmitted_package_id
                                                                AND resubmitted.previous_package_id = NEW.package_id)
    THEN
        RAISE EXCEPTION 'a correction is resubmitted by a package that follows the corrected one';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION information_request_review_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    next_number     INTEGER;
    package_version uuid;
    previous_id     uuid;
    prior_package   uuid;
    prior_state     VARCHAR(32);
BEGIN
    IF TG_OP = 'DELETE' AND record_disposal_in_progress() THEN
        RETURN OLD;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'information request history is append-only';
    END IF;

    IF TG_OP = 'INSERT' THEN
        SELECT COALESCE(MAX(review_number), 0) + 1 INTO next_number
        FROM information_request_review
        WHERE information_request_id = NEW.information_request_id;

        IF NEW.review_number <> next_number THEN
            RAISE EXCEPTION 'a review numbers from one without a gap';
        END IF;

        SELECT template_version_id, previous_package_id INTO package_version, previous_id
        FROM information_request_submission_package
        WHERE id = NEW.package_id;

        IF package_version IS DISTINCT FROM NEW.template_version_id THEN
            RAISE EXCEPTION 'a review pins the template version its package froze';
        END IF;

        IF NEW.prior_review_id IS NOT NULL THEN
            SELECT package_id, state INTO prior_package, prior_state
            FROM information_request_review
            WHERE id = NEW.prior_review_id;

            IF NEW.kind = 'RESUBMISSION'
                AND (prior_package IS DISTINCT FROM previous_id OR prior_state <> 'CHANGES_REQUESTED')
            THEN
                RAISE EXCEPTION 'a resubmission review follows the review that requested changes of the package it resubmits';
            END IF;

            IF NEW.kind IN ('RECONSIDERATION', 'APPEAL')
                AND (prior_package IS DISTINCT FROM NEW.package_id
                    OR prior_state NOT IN ('REJECTED', 'CHANGES_REQUESTED'))
            THEN
                RAISE EXCEPTION 'a reconsideration or appeal follows a settled review of the same package';
            END IF;
        END IF;

        RETURN NEW;
    END IF;

    IF OLD.state NOT IN ('PENDING', 'IN_REVIEW') THEN
        RAISE EXCEPTION 'a settled review is immutable';
    END IF;

    IF NEW.id <> OLD.id
        OR NEW.information_request_id <> OLD.information_request_id
        OR NEW.package_id <> OLD.package_id
        OR NEW.review_number <> OLD.review_number
        OR NEW.kind <> OLD.kind
        OR NEW.prior_review_id IS DISTINCT FROM OLD.prior_review_id
        OR NEW.template_version_id <> OLD.template_version_id
        OR NEW.opening_reason IS DISTINCT FROM OLD.opening_reason
        OR NEW.opened_by_principal_kind <> OLD.opened_by_principal_kind
        OR NEW.opened_by_principal_id <> OLD.opened_by_principal_id
        OR NEW.opened_at <> OLD.opened_at
    THEN
        RAISE EXCEPTION 'a review keeps the identity it opened with';
    END IF;

    IF NEW.review_revision <= OLD.review_revision THEN
        RAISE EXCEPTION 'a review revision only moves forward';
    END IF;

    IF OLD.state = 'IN_REVIEW' AND NEW.state IN ('PENDING', 'WITHDRAWN') THEN
        RAISE EXCEPTION 'a review in progress cannot return to pending or be withdrawn';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION information_request_review_assignment_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' AND record_disposal_in_progress() THEN
        RETURN OLD;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'information request history is append-only';
    END IF;

    IF TG_OP = 'INSERT' THEN
        IF NOT EXISTS (SELECT 1
                       FROM information_request_party party
                       WHERE party.id = NEW.reviewer_party_id
                         AND party.information_request_id = NEW.information_request_id
                         AND party.role_key = 'REVIEWER')
        THEN
            RAISE EXCEPTION 'a review assignment names a reviewer party of its request';
        END IF;

        IF NOT EXISTS (SELECT 1
                       FROM information_request_template_review_stage stage
                                JOIN information_request_review review ON review.id = NEW.review_id
                       WHERE stage.id = NEW.template_review_stage_id
                         AND stage.template_version_id = review.template_version_id
                         AND stage.stage_key = NEW.stage_key)
        THEN
            RAISE EXCEPTION 'a review assignment names a stage of its review''s template version';
        END IF;

        IF EXISTS (SELECT 1
                   FROM information_request_review review
                   WHERE review.id = NEW.review_id
                     AND review.state NOT IN ('PENDING', 'IN_REVIEW'))
        THEN
            RAISE EXCEPTION 'a settled review takes no new assignment';
        END IF;

        RETURN NEW;
    END IF;

    IF OLD.state <> 'ACTIVE' THEN
        RAISE EXCEPTION 'an assignment that has left its stage does not change again';
    END IF;

    IF NEW.id <> OLD.id
        OR NEW.review_id <> OLD.review_id
        OR NEW.information_request_id <> OLD.information_request_id
        OR NEW.template_review_stage_id <> OLD.template_review_stage_id
        OR NEW.stage_key <> OLD.stage_key
        OR NEW.reviewer_party_id <> OLD.reviewer_party_id
        OR NEW.reviewer_principal_kind <> OLD.reviewer_principal_kind
        OR NEW.reviewer_principal_id <> OLD.reviewer_principal_id
        OR NEW.delegated_from_assignment_id IS DISTINCT FROM OLD.delegated_from_assignment_id
        OR NEW.due_at IS DISTINCT FROM OLD.due_at
        OR NEW.assigned_by_principal_kind <> OLD.assigned_by_principal_kind
        OR NEW.assigned_by_principal_id <> OLD.assigned_by_principal_id
        OR NEW.assigned_at <> OLD.assigned_at
    THEN
        RAISE EXCEPTION 'a review assignment keeps the identity it was made with';
    END IF;

    IF NEW.draft_revision < OLD.draft_revision THEN
        RAISE EXCEPTION 'a worksheet revision only moves forward';
    END IF;

    IF OLD.decided_at IS NOT NULL AND (NEW.decided_at IS DISTINCT FROM OLD.decided_at OR NEW.state <> 'ACTIVE') THEN
        RAISE EXCEPTION 'an assignment that recorded its decisions keeps them';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION information_request_review_draft_item_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    candidate_assignment uuid;
    candidate_review     uuid;
    candidate_item       uuid;
BEGIN
    IF TG_OP = 'DELETE' AND record_disposal_in_progress() THEN
        RETURN OLD;
    END IF;
    IF TG_OP = 'DELETE' THEN
        candidate_assignment := OLD.assignment_id;
        candidate_review := OLD.review_id;
        candidate_item := OLD.submission_item_id;
    ELSE
        candidate_assignment := NEW.assignment_id;
        candidate_review := NEW.review_id;
        candidate_item := NEW.submission_item_id;
    END IF;

    IF NOT EXISTS (SELECT 1
                   FROM information_request_review_assignment assignment
                   WHERE assignment.id = candidate_assignment
                     AND assignment.state = 'ACTIVE'
                     AND assignment.decided_at IS NULL)
    THEN
        RAISE EXCEPTION 'only an active assignment that has not recorded its decisions keeps a worksheet';
    END IF;

    IF NOT EXISTS (SELECT 1
                   FROM information_request_submission_item item
                            JOIN information_request_review review ON review.package_id = item.package_id
                   WHERE item.id = candidate_item
                     AND review.id = candidate_review)
    THEN
        RAISE EXCEPTION 'a worksheet names an item of its review''s package';
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION information_request_evidence_artifact_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' AND record_disposal_in_progress() THEN
        RETURN OLD;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'information request evidence artifact is retained';
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
        OR NEW.information_request_id IS DISTINCT FROM OLD.information_request_id
        OR NEW.information_request_requirement_id IS DISTINCT FROM OLD.information_request_requirement_id
        OR NEW.artifact_key IS DISTINCT FROM OLD.artifact_key
        OR NEW.created_by_principal_kind IS DISTINCT FROM OLD.created_by_principal_kind
        OR NEW.created_by_principal_id IS DISTINCT FROM OLD.created_by_principal_id
        OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'information request evidence artifact identity is immutable';
    END IF;

    IF NEW.artifact_revision < OLD.artifact_revision THEN
        RAISE EXCEPTION 'information request evidence artifact revision cannot move backwards';
    END IF;

    IF NEW.collection_state IS DISTINCT FROM OLD.collection_state
        AND NEW.collection_state IN ('ACTIVE', 'WITHDRAWN', 'REMOVED')
        AND NOT (
            (OLD.collection_state = 'ACTIVE' AND NEW.collection_state IN ('WITHDRAWN', 'REMOVED'))
                OR (OLD.collection_state = 'WITHDRAWN' AND NEW.collection_state = 'REMOVED')
            ) THEN
        RAISE EXCEPTION 'information request evidence artifact state cannot move from % to %',
            OLD.collection_state, NEW.collection_state;
    END IF;

    IF NEW.collection_state = OLD.collection_state
        AND OLD.collection_state <> 'ACTIVE'
        AND (NEW.state_changed_at IS DISTINCT FROM OLD.state_changed_at
            OR NEW.state_changed_by_principal_kind IS DISTINCT FROM OLD.state_changed_by_principal_kind
            OR NEW.state_changed_by_principal_id IS DISTINCT FROM OLD.state_changed_by_principal_id
            OR NEW.state_reason IS DISTINCT FROM OLD.state_reason) THEN
        RAISE EXCEPTION 'information request evidence artifact state change is immutable';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION information_request_requirement_binding_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' AND record_disposal_in_progress() THEN
        RETURN OLD;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'information request history is append-only';
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
        OR NEW.information_request_id IS DISTINCT FROM OLD.information_request_id
        OR NEW.source_template_requirement_id IS DISTINCT FROM OLD.source_template_requirement_id
        OR NEW.occurrence_path IS DISTINCT FROM OLD.occurrence_path
        OR NEW.created_at IS DISTINCT FROM OLD.created_at
    THEN
        RAISE EXCEPTION 'a runtime requirement keeps its identity; only its effective binding advances';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_request_template_version earlier
                 JOIN information_request_template_version later
                      ON later.template_definition_id = earlier.template_definition_id
        WHERE earlier.id = OLD.source_template_version_id
          AND later.id = NEW.source_template_version_id
          AND later.version_number > earlier.version_number
    ) THEN
        RAISE EXCEPTION 'a runtime requirement binding advances only to a later Version of its Template';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION request_template_configuration_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' AND record_disposal_in_progress() THEN
        RETURN OLD;
    END IF;
    IF TG_OP <> 'INSERT' THEN
        PERFORM request_template_draft_version_required(OLD.template_version_id);
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    PERFORM request_template_draft_version_required(NEW.template_version_id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION request_template_version_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' AND record_disposal_in_progress() THEN
        RETURN OLD;
    END IF;
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'a published information request template version is immutable and cannot be removed';
        END IF;
        RETURN OLD;
    END IF;

    IF NEW.status = 'PUBLISHED' AND OLD.status <> 'PUBLISHED' THEN
        PERFORM request_template_state_default_attestation_policies(NEW.id);
        PERFORM request_template_state_default_review_stage(NEW.id);
        PERFORM request_template_publication_completeness(NEW.id, NEW.schema_version_id);
    END IF;

    IF OLD.status = 'DRAFT' THEN
        RETURN NEW;
    END IF;

    IF OLD.status = 'RETIRED' THEN
        RAISE EXCEPTION 'a retired information request template version is immutable';
    END IF;

    IF NEW.status <> 'RETIRED'
        OR NEW.id <> OLD.id
        OR NEW.template_definition_id <> OLD.template_definition_id
        OR NEW.version_number <> OLD.version_number
        OR NEW.schema_version_id IS DISTINCT FROM OLD.schema_version_id
        OR NEW.submission_mode IS DISTINCT FROM OLD.submission_mode
        OR NEW.submission_stage_ordering IS DISTINCT FROM OLD.submission_stage_ordering
        OR NEW.review_stage_ordering IS DISTINCT FROM OLD.review_stage_ordering
        OR NEW.fact_reuse_purpose_key IS DISTINCT FROM OLD.fact_reuse_purpose_key
        OR NEW.published_at IS DISTINCT FROM OLD.published_at
        OR NEW.published_by_app_user_id IS DISTINCT FROM OLD.published_by_app_user_id
        OR NEW.created_at IS DISTINCT FROM OLD.created_at
        OR NEW.created_by_app_user_id IS DISTINCT FROM OLD.created_by_app_user_id
    THEN
        RAISE EXCEPTION 'a published information request template version is immutable except for retirement';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION record_dispose_information_request(disposal_claim_id uuid)
    RETURNS TEXT AS
$$
DECLARE
    claim             record_disposal_claim%ROWTYPE;
    target_request    uuid;
    target_assignment uuid;
    target_definition uuid;
    export_ids        uuid[];
    removed           jsonb := '{}'::jsonb;
    affected          BIGINT;
    package_count     BIGINT;
    request_count     BIGINT;
BEGIN
    SELECT * INTO claim FROM record_disposal_claim WHERE id = disposal_claim_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'a disposal names an existing claim';
    END IF;
    IF claim.state <> 'OBJECTS_DELETED' OR claim.resource_type <> 'INFORMATION_REQUEST' THEN
        RAISE EXCEPTION 'a record is finalized only after its claimed objects are deleted';
    END IF;
    target_request := claim.resource_id;
    PERFORM set_config('docuhyphen.record_disposal_claim', disposal_claim_id::text, true);

    SELECT id INTO target_assignment
    FROM schema_assignment
    WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = target_request;
    SELECT id INTO target_definition
    FROM information_request_template_definition
    WHERE origin_request_id = target_request;
    export_ids := ARRAY(SELECT export_id
                        FROM information_request_record_export_source
                        WHERE information_request_id = target_request
                        UNION
                        SELECT id
                        FROM information_request_record_export
                        WHERE information_request_id = target_request);

    DELETE FROM information_request_record_export_source WHERE export_id = ANY (export_ids);
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_record_export_source', affected);
    DELETE FROM information_request_record_export WHERE id = ANY (export_ids);
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_record_export', affected);

    DELETE FROM information_request_notice_sequence_allocation
    WHERE outbound_notice_id IN (SELECT id FROM information_request_outbound_notice WHERE information_request_id = target_request);
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_notice_sequence_allocation', affected);
    DELETE FROM information_request_notice_delivery_attempt WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_notice_delivery_attempt', affected);
    DELETE FROM information_request_outbound_notice WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_outbound_notice', affected);
    DELETE FROM information_request_notice_claim WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_notice_claim', affected);
    DELETE FROM information_request_notice_intent WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_notice_intent', affected);

    DELETE FROM information_request_clock_event WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_clock_event', affected);
    DELETE FROM information_request_clock WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_clock', affected);

    DELETE FROM information_request_accepted_fact_revocation
    WHERE fact_id IN (SELECT id FROM information_request_accepted_fact WHERE source_information_request_id = target_request);
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_accepted_fact_revocation', affected);
    DELETE FROM information_request_accepted_fact WHERE source_information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_accepted_fact', affected);
    DELETE FROM information_request_business_decision WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_business_decision', affected);

    DELETE FROM information_request_review_remediation WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_review_remediation', affected);
    DELETE FROM information_request_review_comment WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_review_comment', affected);
    DELETE FROM information_request_correction_evidence
    WHERE correction_id IN (SELECT id FROM information_request_correction WHERE information_request_id = target_request);
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_correction_evidence', affected);
    DELETE FROM information_request_correction_item WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_correction_item', affected);
    DELETE FROM information_request_correction WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_correction', affected);
    DELETE FROM information_request_review_finding WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_review_finding', affected);
    DELETE FROM information_request_review_decision WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_review_decision', affected);
    DELETE FROM information_request_review_draft_item
    WHERE review_id IN (SELECT id FROM information_request_review WHERE information_request_id = target_request);
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_review_draft_item', affected);
    DELETE FROM information_request_review_assignment WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_review_assignment', affected);
    DELETE FROM information_request_review WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_review', affected);

    DELETE FROM information_request_carry_forward WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_carry_forward', affected);
    DELETE FROM information_request_lineage WHERE successor_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_lineage', affected);
    DELETE FROM information_request_refresh_rule WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_refresh_rule', affected);
    DELETE FROM information_request_recurrence WHERE origin_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_recurrence', affected);

    DELETE FROM information_request_submission_package_attestation WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_submission_package_attestation', affected);
    DELETE FROM information_request_submission_attestation WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_submission_attestation', affected);
    DELETE FROM information_request_submission_withdrawal WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_submission_withdrawal', affected);
    DELETE FROM information_request_submission_supporting_link WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_submission_supporting_link', affected);
    DELETE FROM information_request_submission_evidence WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_submission_evidence', affected);
    DELETE FROM information_request_submission_item WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_submission_item', affected);

    DELETE FROM information_request_supporting_evidence_link WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_supporting_evidence_link', affected);
    DELETE FROM information_request_evidence_assessment WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_evidence_assessment', affected);
    DELETE FROM information_request_evidence_version WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_evidence_version', affected);
    DELETE FROM information_request_evidence_artifact WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_evidence_artifact', affected);

    DELETE FROM information_request_amendment_change WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_amendment_change', affected);
    DELETE FROM information_request_response WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_response', affected);
    DELETE FROM information_request_amendment WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_amendment', affected);
    DELETE FROM information_request_group_occurrence WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_group_occurrence', affected);

    DELETE FROM information_request_requirement_current
    WHERE information_request_requirement_id IN (SELECT id FROM information_request_requirement WHERE information_request_id = target_request);
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_requirement_current', affected);
    DELETE FROM information_request_delegated_authority WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_delegated_authority', affected);
    DELETE FROM information_request_requirement_revision WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_requirement_revision', affected);
    DELETE FROM information_request_requirement WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_requirement', affected);
    DELETE FROM information_request_document_placeholder WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_document_placeholder', affected);
    DELETE FROM information_request_transition WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_transition', affected);
    DELETE FROM information_request_party WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_party', affected);
    DELETE FROM request_execution_usage_reservation
    WHERE grant_id IN (SELECT id FROM request_execution_grant WHERE request_id = target_request);
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('request_execution_usage_reservation', affected);
    DELETE FROM request_execution_grant WHERE request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('request_execution_grant', affected);

    IF target_assignment IS NOT NULL THEN
        DELETE FROM field_value_revision WHERE schema_assignment_id = target_assignment;
        GET DIAGNOSTICS affected = ROW_COUNT;
        removed := removed || jsonb_build_object('field_value_revision', affected);
        DELETE FROM field_value_selection
        WHERE field_value_id IN (SELECT id FROM field_value WHERE schema_assignment_id = target_assignment);
        GET DIAGNOSTICS affected = ROW_COUNT;
        removed := removed || jsonb_build_object('field_value_selection', affected);
        DELETE FROM field_value WHERE schema_assignment_id = target_assignment;
        GET DIAGNOSTICS affected = ROW_COUNT;
        removed := removed || jsonb_build_object('field_value', affected);
        DELETE FROM field_value_set WHERE schema_assignment_id = target_assignment;
        GET DIAGNOSTICS affected = ROW_COUNT;
        removed := removed || jsonb_build_object('field_value_set', affected);
        DELETE FROM schema_assignment WHERE id = target_assignment;
        GET DIAGNOSTICS affected = ROW_COUNT;
        removed := removed || jsonb_build_object('schema_assignment', affected);
    END IF;

    DELETE FROM document_version
    WHERE id IN (SELECT document_version_id FROM record_disposal_object WHERE claim_id = disposal_claim_id AND NOT retained);
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('document_version', affected);
    DELETE FROM document
    WHERE id IN (SELECT document_id FROM record_disposal_object WHERE claim_id = disposal_claim_id AND NOT retained)
      AND NOT EXISTS (SELECT 1 FROM document_version remaining WHERE remaining.document_id = document.id);
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('document', affected);

    WITH packages AS (DELETE FROM information_request_submission_package WHERE information_request_id = target_request RETURNING 1),
         requests AS (DELETE FROM information_request WHERE id = target_request RETURNING 1)
    SELECT (SELECT COUNT(*) FROM packages), (SELECT COUNT(*) FROM requests)
    INTO package_count, request_count;
    removed := removed || jsonb_build_object('information_request_submission_package', package_count,
                                             'information_request', request_count);

    IF target_definition IS NOT NULL THEN
        DELETE FROM information_request_template_attestation_role
        WHERE template_version_id IN (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
        DELETE FROM information_request_template_condition_predicate_literal
        WHERE template_version_id IN (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
        DELETE FROM information_request_template_evidence_accepted_value
        WHERE template_version_id IN (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
        DELETE FROM information_request_template_review_stage_section
        WHERE template_version_id IN (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
        DELETE FROM information_request_template_attestation_policy
        WHERE template_version_id IN (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
        DELETE FROM information_request_template_condition_predicate
        WHERE template_version_id IN (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
        DELETE FROM information_request_template_evidence_policy
        WHERE template_version_id IN (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
        DELETE FROM information_request_template_binding_disposition
        WHERE template_version_id IN (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
        DELETE FROM information_request_template_binding_evidence_link
        WHERE template_version_id IN (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
        DELETE FROM information_request_template_binding_substitute
        WHERE template_version_id IN (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
        DELETE FROM information_request_template_condition_rule
        WHERE template_version_id IN (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
        DELETE FROM information_request_template_requirement_binding
        WHERE template_version_id IN (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
        DELETE FROM information_request_template_review_stage
        WHERE template_version_id IN (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
        DELETE FROM information_request_template_requirement_group
        WHERE template_version_id IN (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
        DELETE FROM information_request_template_section
        WHERE template_version_id IN (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
        DELETE FROM information_request_template_version_capability
        WHERE template_version_id IN (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
        DELETE FROM information_request_template_version WHERE template_definition_id = target_definition;
        DELETE FROM information_request_template_requirement WHERE template_definition_id = target_definition;
        DELETE FROM information_request_template_definition WHERE id = target_definition;
        GET DIAGNOSTICS affected = ROW_COUNT;
        removed := removed || jsonb_build_object('information_request_template_definition', affected);
    END IF;

    INSERT INTO record_disposal_tombstone (id, claim_id, resource_type, resource_id, owner_kind, owner_id, basis,
                                           removed_rows_json, deleted_object_count, retained_object_count, disposed_at)
    SELECT gen_random_uuid(),
           claim.id,
           claim.resource_type,
           claim.resource_id,
           claim.owner_kind,
           claim.owner_id,
           claim.basis,
           removed::text,
           (SELECT COUNT(*) FROM record_disposal_object WHERE claim_id = claim.id AND NOT retained),
           (SELECT COUNT(*) FROM record_disposal_object WHERE claim_id = claim.id AND retained),
           CURRENT_TIMESTAMP;

    UPDATE record_disposal_claim
    SET state          = 'FINALIZED',
        finalized_at   = CURRENT_TIMESTAMP,
        claim_revision = claim_revision + 1
    WHERE id = claim.id;

    PERFORM set_config('docuhyphen.record_disposal_claim', '', true);
    RETURN removed::text;
END;
$$ LANGUAGE plpgsql;
