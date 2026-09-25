ALTER TABLE information_request_requirement
    ADD CONSTRAINT uq_information_request_requirement_scope
        UNIQUE (id, information_request_id);

CREATE TABLE information_request_evidence_artifact
(
    id                                 uuid         NOT NULL,
    information_request_id             uuid         NOT NULL,
    information_request_requirement_id uuid         NOT NULL,
    artifact_key                       VARCHAR(128) NOT NULL,
    artifact_revision                  BIGINT       NOT NULL DEFAULT 1,
    created_at                         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_evidence_artifact_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_evidence_artifact_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT information_request_evidence_artifact_requirement_fkey
        FOREIGN KEY (information_request_requirement_id, information_request_id)
            REFERENCES information_request_requirement (id, information_request_id),
    CONSTRAINT ck_information_request_evidence_artifact_key CHECK (BTRIM(artifact_key) <> ''),
    CONSTRAINT ck_information_request_evidence_artifact_revision CHECK (artifact_revision >= 1),
    CONSTRAINT ux_information_request_evidence_artifact_key
        UNIQUE (information_request_requirement_id, artifact_key),
    CONSTRAINT uq_information_request_evidence_artifact_scope UNIQUE (id, information_request_id)
);

CREATE INDEX ix_information_request_evidence_artifact_request
    ON information_request_evidence_artifact (information_request_id, created_at);

CREATE INDEX ix_information_request_evidence_artifact_requirement
    ON information_request_evidence_artifact (information_request_requirement_id);

CREATE TABLE information_request_evidence_version
(
    id                      uuid         NOT NULL,
    evidence_artifact_id    uuid         NOT NULL,
    information_request_id  uuid         NOT NULL,
    version_number          INTEGER      NOT NULL,
    source_kind             VARCHAR(32)  NOT NULL,
    document_version_id     uuid,
    external_reference_type VARCHAR(64),
    external_reference_value VARCHAR(512),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_evidence_version_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_evidence_version_artifact_fkey
        FOREIGN KEY (evidence_artifact_id, information_request_id)
            REFERENCES information_request_evidence_artifact (id, information_request_id),
    CONSTRAINT information_request_evidence_version_document_version_fkey
        FOREIGN KEY (document_version_id) REFERENCES document_version (id),
    CONSTRAINT ck_information_request_evidence_version_kind CHECK (
        source_kind IN ('DOCUMENT_VERSION', 'EXTERNAL_REFERENCE')
        ),
    CONSTRAINT ck_information_request_evidence_version_number CHECK (version_number >= 1),
    CONSTRAINT ck_information_request_evidence_version_source CHECK (
        (source_kind = 'DOCUMENT_VERSION'
            AND document_version_id IS NOT NULL
            AND external_reference_type IS NULL
            AND external_reference_value IS NULL) OR
        (source_kind = 'EXTERNAL_REFERENCE'
            AND document_version_id IS NULL
            AND BTRIM(COALESCE(external_reference_type, '')) <> ''
            AND BTRIM(COALESCE(external_reference_value, '')) <> '') OR
        source_kind NOT IN ('DOCUMENT_VERSION', 'EXTERNAL_REFERENCE')
        ),
    CONSTRAINT ux_information_request_evidence_version_number
        UNIQUE (evidence_artifact_id, version_number)
);

CREATE INDEX ix_information_request_evidence_version_artifact
    ON information_request_evidence_version (evidence_artifact_id, version_number);

CREATE INDEX ix_information_request_evidence_version_document_version
    ON information_request_evidence_version (document_version_id)
    WHERE document_version_id IS NOT NULL;

CREATE FUNCTION information_request_evidence_version_order_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    next_version INTEGER;
BEGIN
    SELECT COALESCE(MAX(version_number), 0) + 1
    INTO next_version
    FROM information_request_evidence_version
    WHERE evidence_artifact_id = NEW.evidence_artifact_id;

    IF NEW.version_number > next_version THEN
        RAISE EXCEPTION 'evidence version numbers must be contiguous, expected %', next_version;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_evidence_version_order_write
    BEFORE INSERT
    ON information_request_evidence_version
    FOR EACH ROW
EXECUTE FUNCTION information_request_evidence_version_order_guard();

CREATE TRIGGER information_request_evidence_version_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_evidence_version
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();
