ALTER TABLE information_request
    ADD COLUMN satisfied_at            TIMESTAMPTZ,
    ADD COLUMN satisfied_by_package_id uuid;

ALTER TABLE information_request_transition
    DROP CONSTRAINT ck_information_request_transition_mutation;

ALTER TABLE information_request_transition
    ADD CONSTRAINT ck_information_request_transition_mutation CHECK (
        mutation IN ('CREATE_DRAFT', 'ISSUE', 'RECORD_FIRST_VIEW', 'SAVE_RESPONSE',
                     'ATTEST_RESPONSE', 'ADMINISTER_EVIDENCE', 'SUBMIT', 'START_REVIEW',
                     'REQUEST_CORRECTION', 'CLOSE', 'AMEND', 'REASSIGN', 'CANCEL',
                     'SUPERSEDE', 'EXPIRE', 'WITHDRAW_SUBMISSION', 'CREATE_SUCCESSOR',
                     'SCHEDULE_FOLLOW_UP')
        );

CREATE TABLE information_request_submission_package
(
    id                          uuid        NOT NULL,
    information_request_id      uuid        NOT NULL,
    package_number              INTEGER     NOT NULL,
    stage_key                   VARCHAR(128),
    template_version_id         uuid        NOT NULL,
    schema_version_id           uuid,
    content_hash_sha256         VARCHAR(64) NOT NULL,
    manifest_hash_sha256        VARCHAR(64) NOT NULL,
    review_required             BOOLEAN     NOT NULL,
    completes_request           BOOLEAN     NOT NULL,
    previous_package_id         uuid,
    submitted_by_principal_kind VARCHAR(32) NOT NULL,
    submitted_by_principal_id   uuid        NOT NULL,
    submitted_by_session_ref    VARCHAR(64),
    submitted_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_submission_package_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_submission_package_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT information_request_submission_package_version_fkey
        FOREIGN KEY (template_version_id) REFERENCES information_request_template_version (id),
    CONSTRAINT information_request_submission_package_schema_fkey
        FOREIGN KEY (schema_version_id) REFERENCES schema_version (id),
    CONSTRAINT ux_information_request_submission_package_number
        UNIQUE (information_request_id, package_number),
    CONSTRAINT uq_information_request_submission_package_request UNIQUE (id, information_request_id),
    CONSTRAINT information_request_submission_package_previous_fkey
        FOREIGN KEY (previous_package_id, information_request_id)
            REFERENCES information_request_submission_package (id, information_request_id),
    CONSTRAINT ck_information_request_submission_package_number CHECK (package_number >= 1),
    CONSTRAINT ck_information_request_submission_package_stage CHECK (
        stage_key IS NULL OR BTRIM(stage_key) <> ''
        ),
    CONSTRAINT ck_information_request_submission_package_hashes CHECK (
        content_hash_sha256 ~ '^[0-9a-f]{64}$' AND manifest_hash_sha256 ~ '^[0-9a-f]{64}$'
        ),
    CONSTRAINT ck_information_request_submission_package_submitter CHECK (
        submitted_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                        'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE INDEX ix_information_request_submission_package_request
    ON information_request_submission_package (information_request_id, stage_key, package_number);

ALTER TABLE information_request
    ADD CONSTRAINT information_request_satisfied_by_package_fkey
        FOREIGN KEY (satisfied_by_package_id, id)
            REFERENCES information_request_submission_package (id, information_request_id),
    ADD CONSTRAINT ck_information_request_satisfaction CHECK (
        (satisfied_at IS NULL) = (satisfied_by_package_id IS NULL)
        );

CREATE FUNCTION information_request_submission_package_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    pinned_version uuid;
    version_mode VARCHAR(32);
    next_number INTEGER;
    previous_stage VARCHAR(128);
BEGIN
    SELECT template_version_id INTO pinned_version
    FROM information_request
    WHERE id = NEW.information_request_id;

    IF pinned_version IS DISTINCT FROM NEW.template_version_id THEN
        RAISE EXCEPTION 'a submission package pins the template version its request is on';
    END IF;

    SELECT COALESCE(MAX(package_number), 0) + 1 INTO next_number
    FROM information_request_submission_package
    WHERE information_request_id = NEW.information_request_id;

    IF NEW.package_number <> next_number THEN
        RAISE EXCEPTION 'a submission package numbers from one without a gap';
    END IF;

    SELECT submission_mode INTO version_mode
    FROM information_request_template_version
    WHERE id = NEW.template_version_id;

    IF version_mode = 'WHOLE_PACKAGE' AND NEW.stage_key IS NOT NULL THEN
        RAISE EXCEPTION 'a whole-package package names no stage';
    END IF;

    IF version_mode = 'STAGED' AND NOT EXISTS (SELECT 1
                                               FROM information_request_template_section section
                                               WHERE section.template_version_id = NEW.template_version_id
                                                 AND section.submission_stage_key = NEW.stage_key)
    THEN
        RAISE EXCEPTION 'a staged package names a stage its template version defines';
    END IF;

    IF NEW.previous_package_id IS NOT NULL THEN
        SELECT stage_key INTO previous_stage
        FROM information_request_submission_package
        WHERE id = NEW.previous_package_id;

        IF previous_stage IS DISTINCT FROM NEW.stage_key THEN
            RAISE EXCEPTION 'a resubmission follows a package of the same stage';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_submission_package_guard_write
    BEFORE INSERT
    ON information_request_submission_package
    FOR EACH ROW
EXECUTE FUNCTION information_request_submission_package_guard();

CREATE TABLE information_request_submission_item
(
    id                                 uuid         NOT NULL,
    package_id                         uuid         NOT NULL,
    information_request_id             uuid         NOT NULL,
    information_request_requirement_id uuid         NOT NULL,
    requirement_revision_id            uuid         NOT NULL,
    template_binding_id                uuid         NOT NULL,
    requirement_key                    VARCHAR(128) NOT NULL,
    requirement_type                   VARCHAR(32)  NOT NULL,
    occurrence_path                    VARCHAR(512) NOT NULL,
    completeness_state                 VARCHAR(32)  NOT NULL,
    disposition                        VARCHAR(32)  NOT NULL,
    narrative                          text,
    response_id                        uuid,
    response_revision                  BIGINT,
    responded_by_principal_kind        VARCHAR(32),
    responded_by_principal_id          uuid,
    responded_by_session_ref           VARCHAR(64),
    field_value_set_id                 uuid,
    field_value_revision_id            uuid,
    evidence_state                     VARCHAR(32),
    attestation_state                  VARCHAR(32),
    item_hash_sha256                   VARCHAR(64)  NOT NULL,
    CONSTRAINT information_request_submission_item_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_submission_item_package_fkey
        FOREIGN KEY (package_id, information_request_id)
            REFERENCES information_request_submission_package (id, information_request_id),
    CONSTRAINT information_request_submission_item_requirement_fkey
        FOREIGN KEY (information_request_requirement_id, information_request_id)
            REFERENCES information_request_requirement (id, information_request_id),
    CONSTRAINT information_request_submission_item_revision_fkey
        FOREIGN KEY (requirement_revision_id) REFERENCES information_request_requirement_revision (id),
    CONSTRAINT information_request_submission_item_binding_fkey
        FOREIGN KEY (template_binding_id) REFERENCES information_request_template_requirement_binding (id),
    CONSTRAINT information_request_submission_item_response_fkey
        FOREIGN KEY (response_id) REFERENCES information_request_response (id),
    CONSTRAINT information_request_submission_item_value_set_fkey
        FOREIGN KEY (field_value_set_id) REFERENCES field_value_set (id),
    CONSTRAINT information_request_submission_item_value_revision_fkey
        FOREIGN KEY (field_value_revision_id) REFERENCES field_value_revision (id),
    CONSTRAINT ux_information_request_submission_item_requirement
        UNIQUE (package_id, information_request_requirement_id),
    CONSTRAINT uq_information_request_submission_item_package UNIQUE (id, package_id),
    CONSTRAINT ck_information_request_submission_item_type CHECK (
        requirement_type IN ('FIELD', 'DOCUMENT', 'RESPONSE_ATTESTATION')
        ),
    CONSTRAINT ck_information_request_submission_item_state CHECK (
        completeness_state IN ('COMPLETE', 'OPTIONAL_UNANSWERED', 'HIDDEN')
        ),
    CONSTRAINT ck_information_request_submission_item_disposition CHECK (
        disposition IN ('NOT_ANSWERED', 'PROVIDED', 'PARTIALLY_PROVIDED', 'NOT_APPLICABLE',
                        'UNAVAILABLE', 'EXCEPTION_REQUESTED', 'SATISFIED_BY_REFERENCE', 'WAIVED')
        ),
    CONSTRAINT ck_information_request_submission_item_response CHECK (
        (response_id IS NULL AND response_revision IS NULL AND responded_by_principal_kind IS NULL
            AND responded_by_principal_id IS NULL AND responded_by_session_ref IS NULL)
            OR (response_id IS NOT NULL AND response_revision IS NOT NULL
            AND responded_by_principal_kind IS NOT NULL AND responded_by_principal_id IS NOT NULL)
        ),
    CONSTRAINT ck_information_request_submission_item_field CHECK (
        field_value_revision_id IS NULL OR field_value_set_id IS NOT NULL
        ),
    CONSTRAINT ck_information_request_submission_item_hash CHECK (item_hash_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_information_request_submission_item_requirement
    ON information_request_submission_item (information_request_requirement_id, package_id);

CREATE FUNCTION information_request_submission_item_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_request_requirement_revision revision
                   WHERE revision.id = NEW.requirement_revision_id
                     AND revision.information_request_requirement_id = NEW.information_request_requirement_id
                     AND revision.source_template_binding_id = NEW.template_binding_id)
    THEN
        RAISE EXCEPTION 'a submission item names a revision of its own requirement';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_submission_item_guard_write
    BEFORE INSERT
    ON information_request_submission_item
    FOR EACH ROW
EXECUTE FUNCTION information_request_submission_item_guard();

CREATE TABLE information_request_submission_evidence
(
    id                       uuid        NOT NULL,
    package_id               uuid        NOT NULL,
    item_id                  uuid        NOT NULL,
    information_request_id   uuid        NOT NULL,
    evidence_artifact_id     uuid        NOT NULL,
    evidence_version_id      uuid        NOT NULL,
    evidence_version_number  INTEGER     NOT NULL,
    document_version_id      uuid,
    content_hash_algorithm   VARCHAR(16),
    content_hash             VARCHAR(128),
    content_length           BIGINT,
    content_verification     VARCHAR(16),
    conformance              VARCHAR(32) NOT NULL,
    inspection_assessment_id uuid,
    malware_assessment_id    uuid,
    CONSTRAINT information_request_submission_evidence_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_submission_evidence_item_fkey
        FOREIGN KEY (item_id, package_id) REFERENCES information_request_submission_item (id, package_id),
    CONSTRAINT information_request_submission_evidence_package_fkey
        FOREIGN KEY (package_id, information_request_id)
            REFERENCES information_request_submission_package (id, information_request_id),
    CONSTRAINT information_request_submission_evidence_artifact_fkey
        FOREIGN KEY (evidence_artifact_id) REFERENCES information_request_evidence_artifact (id),
    CONSTRAINT information_request_submission_evidence_version_fkey
        FOREIGN KEY (evidence_version_id) REFERENCES information_request_evidence_version (id),
    CONSTRAINT information_request_submission_evidence_document_fkey
        FOREIGN KEY (document_version_id) REFERENCES document_version (id),
    CONSTRAINT information_request_submission_evidence_inspection_fkey
        FOREIGN KEY (inspection_assessment_id) REFERENCES information_request_evidence_assessment (id),
    CONSTRAINT information_request_submission_evidence_malware_fkey
        FOREIGN KEY (malware_assessment_id) REFERENCES information_request_evidence_assessment (id),
    CONSTRAINT ux_information_request_submission_evidence_version UNIQUE (package_id, evidence_version_id),
    CONSTRAINT ck_information_request_submission_evidence_conformance CHECK (
        conformance IN ('PENDING', 'CONFORMING', 'DEFICIENT', 'QUARANTINED', 'CORRUPT', 'EXPIRED')
        ),
    CONSTRAINT ck_information_request_submission_evidence_content CHECK (
        (document_version_id IS NULL AND content_hash IS NULL AND content_hash_algorithm IS NULL
            AND content_length IS NULL AND content_verification IS NULL)
            OR (document_version_id IS NOT NULL AND content_hash IS NOT NULL
            AND content_hash_algorithm IS NOT NULL AND content_length IS NOT NULL
            AND content_verification IS NOT NULL)
        )
);

CREATE FUNCTION information_request_submission_evidence_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_request_evidence_version version
                            JOIN information_request_evidence_artifact artifact
                                 ON artifact.id = version.evidence_artifact_id
                            JOIN information_request_submission_item item
                                 ON item.id = NEW.item_id
                   WHERE version.id = NEW.evidence_version_id
                     AND version.evidence_artifact_id = NEW.evidence_artifact_id
                     AND version.version_number = NEW.evidence_version_number
                     AND version.document_version_id IS NOT DISTINCT FROM NEW.document_version_id
                     AND artifact.information_request_requirement_id = item.information_request_requirement_id)
    THEN
        RAISE EXCEPTION 'a submitted evidence version answers its item''s requirement';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_submission_evidence_guard_write
    BEFORE INSERT
    ON information_request_submission_evidence
    FOR EACH ROW
EXECUTE FUNCTION information_request_submission_evidence_guard();

CREATE TABLE information_request_submission_supporting_link
(
    id                          uuid NOT NULL,
    package_id                  uuid NOT NULL,
    information_request_id      uuid NOT NULL,
    supporting_evidence_link_id uuid NOT NULL,
    supported_requirement_id    uuid NOT NULL,
    supporting_requirement_id   uuid NOT NULL,
    CONSTRAINT information_request_submission_supporting_link_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_submission_supporting_link_package_fkey
        FOREIGN KEY (package_id, information_request_id)
            REFERENCES information_request_submission_package (id, information_request_id),
    CONSTRAINT information_request_submission_supporting_link_link_fkey
        FOREIGN KEY (supporting_evidence_link_id) REFERENCES information_request_supporting_evidence_link (id),
    CONSTRAINT ux_information_request_submission_supporting_link UNIQUE (package_id, supporting_evidence_link_id)
);

CREATE TABLE information_request_submission_attestation
(
    id                           uuid        NOT NULL,
    information_request_id       uuid        NOT NULL,
    attestation_requirement_id   uuid        NOT NULL,
    requirement_revision_id      uuid        NOT NULL,
    stage_key                    VARCHAR(128),
    party_id                     uuid        NOT NULL,
    party_role                   VARCHAR(32) NOT NULL,
    principal_kind               VARCHAR(32) NOT NULL,
    principal_id                 uuid        NOT NULL,
    session_ref                  VARCHAR(64),
    delegated_authority_id       uuid,
    decision                     VARCHAR(16) NOT NULL,
    refusal_reason               text,
    authentication_strength      VARCHAR(32) NOT NULL,
    external_signature_reference VARCHAR(512),
    attested_content_hash_sha256 VARCHAR(64) NOT NULL,
    statement_hash_sha256        VARCHAR(64) NOT NULL,
    policy_hash_sha256           VARCHAR(64) NOT NULL,
    attested_at                  TIMESTAMPTZ NOT NULL,
    expires_at                   TIMESTAMPTZ,
    sequence_number              INTEGER     NOT NULL,
    CONSTRAINT information_request_submission_attestation_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_submission_attestation_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT information_request_submission_attestation_requirement_fkey
        FOREIGN KEY (attestation_requirement_id, information_request_id)
            REFERENCES information_request_requirement (id, information_request_id),
    CONSTRAINT information_request_submission_attestation_revision_fkey
        FOREIGN KEY (requirement_revision_id) REFERENCES information_request_requirement_revision (id),
    CONSTRAINT information_request_submission_attestation_party_fkey
        FOREIGN KEY (party_id) REFERENCES information_request_party (id),
    CONSTRAINT information_request_submission_attestation_authority_fkey
        FOREIGN KEY (delegated_authority_id) REFERENCES information_request_delegated_authority (id),
    CONSTRAINT uq_information_request_submission_attestation_request UNIQUE (id, information_request_id),
    CONSTRAINT ux_information_request_submission_attestation_sequence
        UNIQUE (information_request_id, sequence_number),
    CONSTRAINT ck_information_request_submission_attestation_decision CHECK (
        decision IN ('ASSENTED', 'REFUSED')
        ),
    CONSTRAINT ck_information_request_submission_attestation_refusal CHECK (
        (decision = 'REFUSED' AND refusal_reason IS NOT NULL AND BTRIM(refusal_reason) <> '')
            OR (decision = 'ASSENTED' AND refusal_reason IS NULL)
        ),
    CONSTRAINT ck_information_request_submission_attestation_role CHECK (
        party_role IN ('SUBJECT', 'CONTRIBUTOR', 'PREPARER', 'ATTESTOR')
        ),
    CONSTRAINT ck_information_request_submission_attestation_principal CHECK (
        principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                           'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        ),
    CONSTRAINT ck_information_request_submission_attestation_strength CHECK (
        authentication_strength IN ('VERIFIED_CONTACT', 'ACCOUNT_SIGN_IN', 'MULTI_FACTOR')
        ),
    CONSTRAINT ck_information_request_submission_attestation_signature CHECK (
        external_signature_reference IS NULL OR BTRIM(external_signature_reference) <> ''
        ),
    CONSTRAINT ck_information_request_submission_attestation_hashes CHECK (
        attested_content_hash_sha256 ~ '^[0-9a-f]{64}$'
            AND statement_hash_sha256 ~ '^[0-9a-f]{64}$'
            AND policy_hash_sha256 ~ '^[0-9a-f]{64}$'
        ),
    CONSTRAINT ck_information_request_submission_attestation_expiry CHECK (
        expires_at IS NULL OR expires_at > attested_at
        ),
    CONSTRAINT ck_information_request_submission_attestation_sequence CHECK (sequence_number >= 1)
);

CREATE INDEX ix_information_request_submission_attestation_requirement
    ON information_request_submission_attestation (attestation_requirement_id, attested_content_hash_sha256);

CREATE FUNCTION information_request_submission_attestation_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    next_number INTEGER;
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_request_requirement requirement
                            JOIN information_request_template_requirement template_requirement
                                 ON template_requirement.id = requirement.source_template_requirement_id
                   WHERE requirement.id = NEW.attestation_requirement_id
                     AND template_requirement.requirement_type = 'RESPONSE_ATTESTATION')
    THEN
        RAISE EXCEPTION 'a submission attestation answers an assertion';
    END IF;

    IF NOT EXISTS (SELECT 1
                   FROM information_request_requirement_revision revision
                   WHERE revision.id = NEW.requirement_revision_id
                     AND revision.information_request_requirement_id = NEW.attestation_requirement_id)
    THEN
        RAISE EXCEPTION 'a submission attestation names a revision of its own assertion';
    END IF;

    IF NOT EXISTS (SELECT 1
                   FROM information_request_party party
                   WHERE party.id = NEW.party_id
                     AND party.information_request_id = NEW.information_request_id)
    THEN
        RAISE EXCEPTION 'a submission attestation is made by a party of its own request';
    END IF;

    SELECT COALESCE(MAX(sequence_number), 0) + 1 INTO next_number
    FROM information_request_submission_attestation
    WHERE information_request_id = NEW.information_request_id;

    IF NEW.sequence_number <> next_number THEN
        RAISE EXCEPTION 'submission attestations number from one without a gap';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_submission_attestation_guard_write
    BEFORE INSERT
    ON information_request_submission_attestation
    FOR EACH ROW
EXECUTE FUNCTION information_request_submission_attestation_guard();

CREATE TABLE information_request_submission_package_attestation
(
    package_id             uuid NOT NULL,
    attestation_id         uuid NOT NULL,
    information_request_id uuid NOT NULL,
    CONSTRAINT information_request_submission_package_attestation_pkey PRIMARY KEY (package_id, attestation_id),
    CONSTRAINT information_request_submission_package_attestation_package_fkey
        FOREIGN KEY (package_id, information_request_id)
            REFERENCES information_request_submission_package (id, information_request_id),
    CONSTRAINT information_request_submission_package_attestation_attestation_fkey
        FOREIGN KEY (attestation_id, information_request_id)
            REFERENCES information_request_submission_attestation (id, information_request_id)
);

CREATE FUNCTION information_request_submission_package_attestation_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_request_submission_attestation attestation
                            JOIN information_request_submission_item item
                                 ON item.information_request_requirement_id = attestation.attestation_requirement_id
                   WHERE attestation.id = NEW.attestation_id
                     AND item.package_id = NEW.package_id)
    THEN
        RAISE EXCEPTION 'a package freezes only attestations of requirements it holds';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_submission_package_attestation_guard_write
    BEFORE INSERT
    ON information_request_submission_package_attestation
    FOR EACH ROW
EXECUTE FUNCTION information_request_submission_package_attestation_guard();

CREATE TABLE information_request_submission_withdrawal
(
    id                          uuid        NOT NULL,
    package_id                  uuid        NOT NULL,
    information_request_id      uuid        NOT NULL,
    reason_code                 VARCHAR(128),
    withdrawn_by_principal_kind VARCHAR(32) NOT NULL,
    withdrawn_by_principal_id   uuid        NOT NULL,
    withdrawn_by_session_ref    VARCHAR(64),
    withdrawn_at                TIMESTAMPTZ NOT NULL,
    CONSTRAINT information_request_submission_withdrawal_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_submission_withdrawal_package_fkey
        FOREIGN KEY (package_id, information_request_id)
            REFERENCES information_request_submission_package (id, information_request_id),
    CONSTRAINT ux_information_request_submission_withdrawal_package UNIQUE (package_id),
    CONSTRAINT ck_information_request_submission_withdrawal_reason CHECK (
        reason_code IS NULL OR BTRIM(reason_code) <> ''
        ),
    CONSTRAINT ck_information_request_submission_withdrawal_actor CHECK (
        withdrawn_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                        'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE TRIGGER information_request_submission_package_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_submission_package
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER information_request_submission_item_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_submission_item
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER information_request_submission_evidence_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_submission_evidence
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER information_request_submission_supporting_link_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_submission_supporting_link
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER information_request_submission_attestation_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_submission_attestation
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER information_request_submission_package_attestation_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_submission_package_attestation
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER information_request_submission_withdrawal_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_submission_withdrawal
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();
