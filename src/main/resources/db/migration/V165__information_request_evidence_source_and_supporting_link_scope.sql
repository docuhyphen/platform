ALTER TABLE information_request_evidence_version
DROP
CONSTRAINT ck_information_request_evidence_version_source,
    DROP
CONSTRAINT ck_information_request_evidence_version_declared_file,
    DROP
CONSTRAINT ck_information_request_evidence_version_kind;

ALTER TABLE information_request_evidence_version
DROP
COLUMN external_reference_type,
    DROP
COLUMN external_reference_value,
    ALTER
COLUMN document_version_id SET NOT NULL;

ALTER TABLE information_request_evidence_version
    ADD CONSTRAINT ck_information_request_evidence_version_kind CHECK (source_kind = 'DOCUMENT_VERSION'),
    ADD CONSTRAINT ck_information_request_evidence_version_declared_file CHECK (
        BTRIM(COALESCE(declared_file_name, '')) <> ''
            AND (declared_media_type IS NULL OR BTRIM(declared_media_type) <> '')
        );

ALTER TABLE information_request_supporting_evidence_link
    ADD CONSTRAINT ux_information_request_supporting_evidence_link_request
        UNIQUE (id, information_request_id),
    ADD CONSTRAINT ux_information_request_supporting_evidence_link_members
        UNIQUE (id, supported_requirement_id, supporting_requirement_id);

ALTER TABLE information_request_submission_supporting_link
DROP
CONSTRAINT information_request_submission_supporting_link_link_fkey;

ALTER TABLE information_request_submission_supporting_link
    ADD CONSTRAINT information_request_submission_supporting_link_link_fkey
        FOREIGN KEY (supporting_evidence_link_id, information_request_id)
            REFERENCES information_request_supporting_evidence_link (id, information_request_id),
    ADD CONSTRAINT information_request_submission_supporting_link_members_fkey
        FOREIGN KEY (supporting_evidence_link_id, supported_requirement_id, supporting_requirement_id)
            REFERENCES information_request_supporting_evidence_link (id, supported_requirement_id, supporting_requirement_id);
