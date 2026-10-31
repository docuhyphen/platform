ALTER TABLE information_request_response_archive
    ADD COLUMN evidence_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE information_request_response_archive
    ADD CONSTRAINT ck_request_response_archive_evidence_count CHECK (evidence_count >= 0);

ALTER TABLE information_request_response_archive
DROP
CONSTRAINT ck_request_response_archive_content;

ALTER TABLE information_request_response_archive
    ADD CONSTRAINT ck_request_response_archive_content CHECK (
        disposition <> 'NOT_ANSWERED' OR narrative IS NOT NULL OR field_value_json IS NOT NULL OR evidence_count > 0
        );

CREATE TABLE information_request_response_archive_evidence
(
    id                      uuid    NOT NULL,
    archive_id              uuid    NOT NULL,
    information_request_id  uuid    NOT NULL,
    evidence_artifact_id    uuid    NOT NULL,
    evidence_version_id     uuid    NOT NULL,
    evidence_version_number INTEGER NOT NULL,
    CONSTRAINT information_request_response_archive_evidence_pkey PRIMARY KEY (id),
    CONSTRAINT request_response_archive_evidence_archive_fkey
        FOREIGN KEY (archive_id) REFERENCES information_request_response_archive (id) ON DELETE CASCADE,
    CONSTRAINT request_response_archive_evidence_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id) ON DELETE CASCADE,
    CONSTRAINT request_response_archive_evidence_artifact_fkey
        FOREIGN KEY (evidence_artifact_id, information_request_id)
            REFERENCES information_request_evidence_artifact (id, information_request_id) ON DELETE CASCADE,
    CONSTRAINT request_response_archive_evidence_version_fkey
        FOREIGN KEY (evidence_version_id) REFERENCES information_request_evidence_version (id) ON DELETE CASCADE,
    CONSTRAINT ux_request_response_archive_evidence_artifact UNIQUE (archive_id, evidence_artifact_id),
    CONSTRAINT ck_request_response_archive_evidence_version CHECK (evidence_version_number >= 1)
);

CREATE INDEX ix_request_response_archive_evidence_version
    ON information_request_response_archive_evidence (evidence_version_id);

CREATE FUNCTION information_request_response_archive_evidence_scope_guard()
    RETURNS TRIGGER AS
    $$
BEGIN
    IF
NOT EXISTS (SELECT 1
                   FROM information_request_response_archive archive
                            JOIN information_request_evidence_artifact artifact
                                 ON artifact.information_request_id = archive.information_request_id
                                     AND artifact.information_request_requirement_id = archive.information_request_requirement_id
                            JOIN information_request_evidence_version version
                                 ON version.evidence_artifact_id = artifact.id
                   WHERE archive.id = NEW.archive_id
                     AND archive.information_request_id = NEW.information_request_id
                     AND artifact.id = NEW.evidence_artifact_id
                     AND version.id = NEW.evidence_version_id
                     AND version.version_number = NEW.evidence_version_number)
    THEN
        RAISE EXCEPTION 'an archived file is a version of a file of the archived Requirement';
END IF;
RETURN NEW;
END;
$$
LANGUAGE plpgsql;

CREATE TRIGGER information_request_response_archive_evidence_scope_write
    BEFORE INSERT
    ON information_request_response_archive_evidence
    FOR EACH ROW
    EXECUTE FUNCTION information_request_response_archive_evidence_scope_guard();

CREATE TRIGGER information_request_response_archive_evidence_append_only
    BEFORE UPDATE OR
DELETE
ON information_request_response_archive_evidence
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();
