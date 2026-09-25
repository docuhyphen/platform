ALTER TABLE information_request_evidence_artifact
    ADD COLUMN created_by_principal_kind       VARCHAR(32) NOT NULL,
    ADD COLUMN created_by_principal_id         uuid        NOT NULL,
    ADD COLUMN collection_state                VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN state_changed_at                TIMESTAMPTZ,
    ADD COLUMN state_changed_by_principal_kind VARCHAR(32),
    ADD COLUMN state_changed_by_principal_id   uuid,
    ADD COLUMN state_reason                    VARCHAR(512),
    ADD CONSTRAINT ck_information_request_evidence_artifact_creator_kind CHECK (
        created_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                      'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        ),
    ADD CONSTRAINT ck_information_request_evidence_artifact_state_value CHECK (
        collection_state IN ('ACTIVE', 'WITHDRAWN', 'REMOVED')
        ),
    ADD CONSTRAINT ck_information_request_evidence_artifact_state_change CHECK (
        (collection_state = 'ACTIVE'
            AND state_changed_at IS NULL
            AND state_changed_by_principal_kind IS NULL
            AND state_changed_by_principal_id IS NULL
            AND state_reason IS NULL) OR
        (collection_state <> 'ACTIVE'
            AND state_changed_at IS NOT NULL
            AND state_changed_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                                    'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
            AND state_changed_by_principal_id IS NOT NULL)
        ),
    ADD CONSTRAINT ck_information_request_evidence_artifact_state_reason CHECK (
        state_reason IS NULL OR BTRIM(state_reason) <> ''
        );

CREATE FUNCTION information_request_evidence_artifact_guard()
    RETURNS TRIGGER AS
$$
BEGIN
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

CREATE TRIGGER information_request_evidence_artifact_guard_write
    BEFORE UPDATE OR DELETE
    ON information_request_evidence_artifact
    FOR EACH ROW
EXECUTE FUNCTION information_request_evidence_artifact_guard();

ALTER TABLE information_request_evidence_version
    ADD COLUMN created_by_principal_kind VARCHAR(32)  NOT NULL,
    ADD COLUMN created_by_principal_id   uuid         NOT NULL,
    ADD COLUMN created_by_session_ref    VARCHAR(255),
    ADD COLUMN declared_file_name        VARCHAR(255),
    ADD COLUMN declared_media_type       VARCHAR(255),
    ADD COLUMN issuer                    VARCHAR(255),
    ADD COLUMN jurisdiction              VARCHAR(64),
    ADD COLUMN language                  VARCHAR(35),
    ADD COLUMN issued_on                 DATE,
    ADD COLUMN expires_on                DATE,
    ADD COLUMN coverage_starts_on        DATE,
    ADD COLUMN coverage_ends_on          DATE,
    ADD COLUMN certification_reference   VARCHAR(255),
    ADD COLUMN signature_reference       VARCHAR(255),
    ADD CONSTRAINT ck_information_request_evidence_version_creator_kind CHECK (
        created_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                      'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        ),
    ADD CONSTRAINT ck_information_request_evidence_version_session_ref CHECK (
        created_by_session_ref IS NULL OR BTRIM(created_by_session_ref) <> ''
        ),
    ADD CONSTRAINT ck_information_request_evidence_version_declared_file CHECK (
        (source_kind = 'DOCUMENT_VERSION'
            AND BTRIM(COALESCE(declared_file_name, '')) <> ''
            AND (declared_media_type IS NULL OR BTRIM(declared_media_type) <> '')) OR
        (source_kind = 'EXTERNAL_REFERENCE'
            AND declared_file_name IS NULL
            AND declared_media_type IS NULL) OR
        source_kind NOT IN ('DOCUMENT_VERSION', 'EXTERNAL_REFERENCE')
        ),
    ADD CONSTRAINT ck_information_request_evidence_version_attribute_text CHECK (
        (issuer IS NULL OR BTRIM(issuer) <> '')
            AND (jurisdiction IS NULL OR BTRIM(jurisdiction) <> '')
            AND (language IS NULL OR BTRIM(language) <> '')
            AND (certification_reference IS NULL OR BTRIM(certification_reference) <> '')
            AND (signature_reference IS NULL OR BTRIM(signature_reference) <> '')
        ),
    ADD CONSTRAINT ck_information_request_evidence_version_validity CHECK (
        issued_on IS NULL OR expires_on IS NULL OR expires_on >= issued_on
        ),
    ADD CONSTRAINT ck_information_request_evidence_version_coverage CHECK (
        (coverage_starts_on IS NULL) = (coverage_ends_on IS NULL)
            AND (coverage_starts_on IS NULL OR coverage_ends_on >= coverage_starts_on)
        );

CREATE FUNCTION information_request_evidence_version_artifact_state_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    artifact_state VARCHAR(16);
BEGIN
    SELECT collection_state
    INTO artifact_state
    FROM information_request_evidence_artifact
    WHERE id = NEW.evidence_artifact_id
        FOR SHARE;

    IF artifact_state IS NOT NULL AND artifact_state <> 'ACTIVE' THEN
        RAISE EXCEPTION 'information request evidence versions are appended only to an active artifact';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_evidence_version_artifact_state_write
    BEFORE INSERT
    ON information_request_evidence_version
    FOR EACH ROW
EXECUTE FUNCTION information_request_evidence_version_artifact_state_guard();
