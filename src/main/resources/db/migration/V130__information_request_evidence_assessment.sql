ALTER TABLE information_request_evidence_version
    ADD CONSTRAINT uq_information_request_evidence_version_scope UNIQUE (id, information_request_id);

CREATE TABLE information_request_evidence_assessment
(
    id                      uuid         NOT NULL,
    information_request_id  uuid         NOT NULL,
    evidence_version_id     uuid         NOT NULL,
    assessment_kind         VARCHAR(32)  NOT NULL,
    outcome                 VARCHAR(32)  NOT NULL,
    content_hash_algorithm  VARCHAR(16)  NOT NULL,
    content_hash            VARCHAR(128) NOT NULL,
    content_length          BIGINT       NOT NULL,
    detected_media_type     VARCHAR(255),
    page_count              INTEGER,
    engine_name             VARCHAR(128),
    engine_version          VARCHAR(64),
    signature_version       VARCHAR(128),
    signatures_published_at TIMESTAMPTZ,
    production_eligible     BOOLEAN      NOT NULL DEFAULT FALSE,
    reused_assessment_id    uuid,
    detail                  VARCHAR(512),
    assessed_at             TIMESTAMPTZ  NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_evidence_assessment_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_evidence_assessment_version_fkey
        FOREIGN KEY (evidence_version_id, information_request_id)
            REFERENCES information_request_evidence_version (id, information_request_id),
    CONSTRAINT information_request_evidence_assessment_reuse_fkey
        FOREIGN KEY (reused_assessment_id) REFERENCES information_request_evidence_assessment (id),
    CONSTRAINT ck_information_request_evidence_assessment_kind CHECK (
        assessment_kind IN ('CONTENT_INSPECTION', 'MALWARE_SCAN')
        ),
    CONSTRAINT ck_information_request_evidence_assessment_outcome CHECK (
        (assessment_kind = 'CONTENT_INSPECTION' AND outcome IN ('INSPECTED', 'CORRUPT', 'ENCRYPTED')) OR
        (assessment_kind = 'MALWARE_SCAN' AND outcome IN ('CLEAN', 'MALWARE_DETECTED', 'ERROR', 'TIMEOUT',
                                                          'UNAVAILABLE', 'STALE_SIGNATURES', 'SKIPPED',
                                                          'INDETERMINATE'))
        ),
    CONSTRAINT ck_information_request_evidence_assessment_digest CHECK (
        content_hash_algorithm = 'SHA_256' AND content_hash ~ '^[0-9a-f]{64}$' AND content_length >= 0
        ),
    CONSTRAINT ck_information_request_evidence_assessment_pages CHECK (page_count IS NULL OR page_count >= 0),
    CONSTRAINT ck_information_request_evidence_assessment_inspection CHECK (
        assessment_kind <> 'CONTENT_INSPECTION' OR
        (engine_name IS NULL AND engine_version IS NULL AND signature_version IS NULL
            AND signatures_published_at IS NULL AND production_eligible = FALSE AND reused_assessment_id IS NULL)
        ),
    CONSTRAINT ck_information_request_evidence_assessment_scan_engine CHECK (
        assessment_kind <> 'MALWARE_SCAN' OR outcome NOT IN ('CLEAN', 'MALWARE_DETECTED') OR
        (BTRIM(COALESCE(engine_name, '')) <> '' AND BTRIM(COALESCE(engine_version, '')) <> ''
            AND BTRIM(COALESCE(signature_version, '')) <> '')
        ),
    CONSTRAINT ck_information_request_evidence_assessment_detail CHECK (detail IS NULL OR BTRIM(detail) <> '')
);

CREATE INDEX ix_information_request_evidence_assessment_version
    ON information_request_evidence_assessment (evidence_version_id, assessment_kind, assessed_at);

CREATE INDEX ix_information_request_evidence_assessment_content
    ON information_request_evidence_assessment (content_hash, assessment_kind, assessed_at);

CREATE FUNCTION information_request_evidence_assessment_content_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    version_source      VARCHAR(32);
    stated_algorithm    VARCHAR(16);
    stated_hash         VARCHAR(128);
    stated_length       BIGINT;
    stated_verification VARCHAR(16);
    reused              information_request_evidence_assessment%ROWTYPE;
BEGIN
    SELECT evidence.source_kind,
           content.content_hash_algorithm,
           content.content_hash,
           content.content_length,
           content.content_verification
    INTO version_source, stated_algorithm, stated_hash, stated_length, stated_verification
    FROM information_request_evidence_version evidence
             LEFT JOIN document_version content ON content.id = evidence.document_version_id
    WHERE evidence.id = NEW.evidence_version_id;

    IF version_source IS NULL THEN
        RETURN NEW;
    END IF;

    IF version_source <> 'DOCUMENT_VERSION' THEN
        RAISE EXCEPTION 'evidence assessments describe file-backed evidence only';
    END IF;

    IF NEW.content_hash_algorithm IS DISTINCT FROM stated_algorithm
        OR NEW.content_hash IS DISTINCT FROM stated_hash
        OR NEW.content_length IS DISTINCT FROM stated_length THEN
        RAISE EXCEPTION 'an evidence assessment describes the exact bytes of its version';
    END IF;

    IF stated_verification IS DISTINCT FROM 'VERIFIED'
        AND (NEW.assessment_kind = 'CONTENT_INSPECTION' OR NEW.outcome = 'CLEAN') THEN
        RAISE EXCEPTION 'opaque evidence content is never inspected or scanned clean';
    END IF;

    IF NEW.reused_assessment_id IS NOT NULL THEN
        SELECT *
        INTO reused
        FROM information_request_evidence_assessment
        WHERE id = NEW.reused_assessment_id;

        IF reused.assessment_kind IS DISTINCT FROM NEW.assessment_kind
            OR reused.content_hash IS DISTINCT FROM NEW.content_hash THEN
            RAISE EXCEPTION 'a reused evidence assessment describes the same kind of assessment of the same bytes';
        END IF;

        IF reused.outcome NOT IN ('CLEAN', 'MALWARE_DETECTED')
            OR reused.outcome IS DISTINCT FROM NEW.outcome
            OR reused.engine_name IS DISTINCT FROM NEW.engine_name
            OR reused.engine_version IS DISTINCT FROM NEW.engine_version
            OR reused.signature_version IS DISTINCT FROM NEW.signature_version
            OR reused.signatures_published_at IS DISTINCT FROM NEW.signatures_published_at
            OR reused.production_eligible IS DISTINCT FROM NEW.production_eligible THEN
            RAISE EXCEPTION 'a reused evidence assessment repeats the settled outcome, engine, and signatures it reuses';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_evidence_assessment_content_write
    BEFORE INSERT
    ON information_request_evidence_assessment
    FOR EACH ROW
EXECUTE FUNCTION information_request_evidence_assessment_content_guard();

CREATE TRIGGER information_request_evidence_assessment_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_evidence_assessment
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();
