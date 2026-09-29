ALTER TABLE information_request_transition
    DROP CONSTRAINT ck_information_request_transition_mutation;

ALTER TABLE information_request_transition
    ADD CONSTRAINT ck_information_request_transition_mutation CHECK (
        mutation IN ('CREATE_DRAFT', 'ISSUE', 'RECORD_FIRST_VIEW', 'START_RESPONSE', 'SAVE_RESPONSE',
                     'RECERTIFY_FACT', 'ATTEST_RESPONSE', 'ADMINISTER_EVIDENCE', 'SUBMIT', 'START_REVIEW',
                     'REQUEST_CORRECTION', 'CLOSE', 'AMEND', 'REASSIGN', 'CANCEL',
                     'SUPERSEDE', 'EXPIRE', 'WITHDRAW_SUBMISSION', 'CREATE_SUCCESSOR',
                     'SCHEDULE_FOLLOW_UP', 'ASSIGN_REVIEWER', 'SAVE_REVIEW_DRAFT',
                     'RECORD_REVIEW_DECISION', 'RECORD_FINDING', 'RECORD_REVIEW_COMMENT',
                     'SETTLE_REVIEW', 'PROMOTE_FACT', 'REVOKE_FACT', 'RECORD_BUSINESS_DECISION',
                     'CHANGE_COMPLETION_GATE', 'START_CLOCK', 'PAUSE_CLOCK', 'RESUME_CLOCK',
                     'EXTEND_CLOCK', 'RECORD_REMINDER', 'RECORD_OVERDUE', 'RECORD_ESCALATION',
                     'ASSIGN_PARTY', 'REVOKE_PARTY', 'SEND_REMINDER', 'REQUEST_EXTERNAL_SOURCE',
                     'RECORD_EXTERNAL_VALUE', 'DECIDE_EXTERNAL_VALUE', 'RECORD_GENERATED_OUTPUT')
        );

CREATE TABLE information_request_connector_exchange
(
    id                                 uuid         NOT NULL,
    information_request_id             uuid         NOT NULL,
    information_request_requirement_id uuid         NOT NULL,
    connector_key                      VARCHAR(128) NOT NULL,
    connector_kind                     VARCHAR(32)  NOT NULL,
    contract_version                   INTEGER      NOT NULL,
    state                              VARCHAR(16)  NOT NULL,
    lookup_reference                   VARCHAR(256),
    external_reference                 VARCHAR(256),
    attempt_count                      INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at                    TIMESTAMPTZ,
    failure_code                       VARCHAR(128),
    requested_by_principal_kind        VARCHAR(32)  NOT NULL,
    requested_by_principal_id          uuid         NOT NULL,
    requested_at                       TIMESTAMPTZ  NOT NULL,
    completed_at                       TIMESTAMPTZ,
    CONSTRAINT information_request_connector_exchange_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_connector_exchange_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT information_request_connector_exchange_requirement_fkey
        FOREIGN KEY (information_request_requirement_id) REFERENCES information_request_requirement (id),
    CONSTRAINT ck_information_request_connector_exchange_key CHECK (connector_key ~ '^[a-z0-9][a-z0-9._-]{0,127}$'),
    CONSTRAINT ck_information_request_connector_exchange_kind CHECK (connector_kind IN ('STRUCTURED_EVIDENCE', 'EXTERNAL_VERIFICATION')),
    CONSTRAINT ck_information_request_connector_exchange_version CHECK (contract_version >= 1),
    CONSTRAINT ck_information_request_connector_exchange_state CHECK (state IN ('REQUESTED', 'PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_information_request_connector_exchange_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_information_request_connector_exchange_lookup CHECK (lookup_reference IS NULL OR BTRIM(lookup_reference) <> ''),
    CONSTRAINT ck_information_request_connector_exchange_outcome CHECK (
        (state IN ('REQUESTED', 'PENDING') AND completed_at IS NULL AND failure_code IS NULL) OR
        (state = 'COMPLETED' AND completed_at IS NOT NULL AND failure_code IS NULL) OR
        (state = 'FAILED' AND completed_at IS NOT NULL AND failure_code IS NOT NULL AND BTRIM(failure_code) <> '')
        ),
    CONSTRAINT ck_information_request_connector_exchange_principal CHECK (
        requested_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                        'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE INDEX ix_information_request_connector_exchange_request
    ON information_request_connector_exchange (information_request_id);

CREATE INDEX ix_information_request_connector_exchange_due
    ON information_request_connector_exchange (next_attempt_at)
    WHERE state IN ('REQUESTED', 'PENDING');

CREATE FUNCTION information_request_connector_exchange_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF record_disposal_in_progress() THEN
            RETURN OLD;
        END IF;
        RAISE EXCEPTION 'information request history is append-only';
    END IF;
    IF OLD.state IN ('COMPLETED', 'FAILED') THEN
        RAISE EXCEPTION 'a finished connector exchange is immutable';
    END IF;
    IF NEW.id <> OLD.id
        OR NEW.information_request_id <> OLD.information_request_id
        OR NEW.information_request_requirement_id <> OLD.information_request_requirement_id
        OR NEW.lookup_reference IS DISTINCT FROM OLD.lookup_reference
        OR NEW.connector_key <> OLD.connector_key
        OR NEW.connector_kind <> OLD.connector_kind
        OR NEW.contract_version <> OLD.contract_version
        OR NEW.requested_by_principal_kind <> OLD.requested_by_principal_kind
        OR NEW.requested_by_principal_id <> OLD.requested_by_principal_id
        OR NEW.requested_at <> OLD.requested_at
        OR NEW.attempt_count < OLD.attempt_count
        OR (NEW.state = 'REQUESTED' AND OLD.state = 'PENDING')
    THEN
        RAISE EXCEPTION 'a connector exchange keeps its identity and only moves forward';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_connector_exchange_guard_write
    BEFORE UPDATE OR DELETE
    ON information_request_connector_exchange
    FOR EACH ROW
EXECUTE FUNCTION information_request_connector_exchange_guard();

CREATE FUNCTION information_request_connector_exchange_scope_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_request_requirement requirement
                   WHERE requirement.id = NEW.information_request_requirement_id
                     AND requirement.information_request_id = NEW.information_request_id)
    THEN
        RAISE EXCEPTION 'a connector exchange names a Requirement of its own request';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_connector_exchange_scope_write
    BEFORE INSERT
    ON information_request_connector_exchange
    FOR EACH ROW
EXECUTE FUNCTION information_request_connector_exchange_scope_guard();

CREATE TABLE information_request_imported_value
(
    id                                 uuid         NOT NULL,
    information_request_id             uuid         NOT NULL,
    information_request_requirement_id uuid         NOT NULL,
    source_kind                        VARCHAR(16)  NOT NULL,
    connector_exchange_id              uuid,
    source_reference                   VARCHAR(256) NOT NULL,
    result_key                         VARCHAR(128) NOT NULL,
    value_type                         VARCHAR(32)  NOT NULL,
    canonical_value                    text         NOT NULL,
    confidence                         VARCHAR(16)  NOT NULL,
    verified_at                        TIMESTAMPTZ,
    expires_at                         TIMESTAMPTZ,
    provenance_reference               VARCHAR(256) NOT NULL,
    recorded_by_principal_kind         VARCHAR(32)  NOT NULL,
    recorded_by_principal_id           uuid         NOT NULL,
    recorded_at                        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT information_request_imported_value_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_imported_value_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT information_request_imported_value_requirement_fkey
        FOREIGN KEY (information_request_requirement_id) REFERENCES information_request_requirement (id),
    CONSTRAINT information_request_imported_value_exchange_fkey
        FOREIGN KEY (connector_exchange_id) REFERENCES information_request_connector_exchange (id),
    CONSTRAINT ck_information_request_imported_value_source CHECK (
        (source_kind = 'MANUAL' AND connector_exchange_id IS NULL) OR
        (source_kind = 'CONNECTOR' AND connector_exchange_id IS NOT NULL)
        ),
    CONSTRAINT ck_information_request_imported_value_text CHECK (
        BTRIM(source_reference) <> '' AND BTRIM(provenance_reference) <> ''
        ),
    CONSTRAINT ck_information_request_imported_value_key CHECK (result_key ~ '^[a-z0-9][a-z0-9._-]{0,127}$'),
    CONSTRAINT ck_information_request_imported_value_type CHECK (value_type IN
        ('SHORT_TEXT', 'LONG_TEXT', 'BOOLEAN', 'INTEGER', 'DECIMAL', 'DATE', 'DATE_TIME',
         'SINGLE_SELECT', 'MULTI_SELECT')),
    CONSTRAINT ck_information_request_imported_value_confidence CHECK (
        confidence IN ('ASSERTED', 'MATCHED', 'VERIFIED') AND
        (confidence = 'ASSERTED' OR verified_at IS NOT NULL)
        ),
    CONSTRAINT ck_information_request_imported_value_expiry CHECK (
        expires_at IS NULL OR verified_at IS NULL OR expires_at > verified_at
        ),
    CONSTRAINT ck_information_request_imported_value_principal CHECK (
        recorded_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                       'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE INDEX ix_information_request_imported_value_request
    ON information_request_imported_value (information_request_id, information_request_requirement_id);

CREATE FUNCTION information_request_imported_value_scope_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_request_requirement requirement
                   WHERE requirement.id = NEW.information_request_requirement_id
                     AND requirement.information_request_id = NEW.information_request_id)
    THEN
        RAISE EXCEPTION 'an imported value belongs to a Requirement of its own request';
    END IF;
    IF NEW.connector_exchange_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM information_request_connector_exchange exchange
        WHERE exchange.id = NEW.connector_exchange_id
          AND exchange.information_request_id = NEW.information_request_id
          AND exchange.state = 'COMPLETED')
    THEN
        RAISE EXCEPTION 'a connector value comes from a completed exchange of its own request';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_imported_value_scope_write
    BEFORE INSERT
    ON information_request_imported_value
    FOR EACH ROW
EXECUTE FUNCTION information_request_imported_value_scope_guard();

CREATE TRIGGER information_request_imported_value_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_imported_value
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TABLE information_request_imported_value_decision
(
    id                        uuid         NOT NULL,
    imported_value_id         uuid         NOT NULL,
    information_request_id    uuid         NOT NULL,
    decision                  VARCHAR(16)  NOT NULL,
    reason_code               VARCHAR(128) NOT NULL,
    decided_by_principal_kind VARCHAR(32)  NOT NULL,
    decided_by_principal_id   uuid         NOT NULL,
    decided_at                TIMESTAMPTZ  NOT NULL,
    CONSTRAINT information_request_imported_value_decision_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_imported_value_decision_value_fkey
        FOREIGN KEY (imported_value_id) REFERENCES information_request_imported_value (id),
    CONSTRAINT information_request_imported_value_decision_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT ux_information_request_imported_value_decision UNIQUE (imported_value_id),
    CONSTRAINT ck_information_request_imported_value_decision CHECK (decision IN ('ACCEPTED', 'REJECTED')),
    CONSTRAINT ck_information_request_imported_value_decision_reason CHECK (BTRIM(reason_code) <> ''),
    CONSTRAINT ck_information_request_imported_value_decision_principal CHECK (
        decided_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                      'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE FUNCTION information_request_imported_value_decision_scope_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_request_imported_value imported
                   WHERE imported.id = NEW.imported_value_id
                     AND imported.information_request_id = NEW.information_request_id)
    THEN
        RAISE EXCEPTION 'a decision belongs to the request of its imported value';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_imported_value_decision_scope_write
    BEFORE INSERT
    ON information_request_imported_value_decision
    FOR EACH ROW
EXECUTE FUNCTION information_request_imported_value_decision_scope_guard();

CREATE TRIGGER information_request_imported_value_decision_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_imported_value_decision
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TABLE information_request_imported_value_discrepancy
(
    id                         uuid        NOT NULL,
    imported_value_id          uuid        NOT NULL,
    information_request_id     uuid        NOT NULL,
    response_id                uuid        NOT NULL,
    response_revision          BIGINT      NOT NULL,
    imported_canonical_value   text        NOT NULL,
    response_canonical_value   text        NOT NULL,
    recorded_by_principal_kind VARCHAR(32) NOT NULL,
    recorded_by_principal_id   uuid        NOT NULL,
    recorded_at                TIMESTAMPTZ NOT NULL,
    CONSTRAINT information_request_imported_value_discrepancy_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_imported_value_discrepancy_value_fkey
        FOREIGN KEY (imported_value_id) REFERENCES information_request_imported_value (id),
    CONSTRAINT information_request_imported_value_discrepancy_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT information_request_imported_value_discrepancy_response_fkey
        FOREIGN KEY (response_id) REFERENCES information_request_response (id),
    CONSTRAINT ux_information_request_imported_value_discrepancy UNIQUE (imported_value_id, response_revision),
    CONSTRAINT ck_information_request_imported_value_discrepancy_differs CHECK (
        response_canonical_value <> imported_canonical_value
        ),
    CONSTRAINT ck_information_request_imported_value_discrepancy_principal CHECK (
        recorded_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                       'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE FUNCTION information_request_imported_value_discrepancy_scope_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_request_imported_value imported
                            JOIN information_request_response response
                                 ON response.information_request_id = imported.information_request_id
                                     AND response.information_request_requirement_id = imported.information_request_requirement_id
                   WHERE imported.id = NEW.imported_value_id
                     AND imported.information_request_id = NEW.information_request_id
                     AND response.id = NEW.response_id)
    THEN
        RAISE EXCEPTION 'a discrepancy compares an imported value with a response of its own Requirement';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_imported_value_discrepancy_scope_write
    BEFORE INSERT
    ON information_request_imported_value_discrepancy
    FOR EACH ROW
EXECUTE FUNCTION information_request_imported_value_discrepancy_scope_guard();

CREATE TRIGGER information_request_imported_value_discrepancy_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_imported_value_discrepancy
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TABLE information_request_imported_value_discrepancy_resolution
(
    id                         uuid         NOT NULL,
    discrepancy_id             uuid         NOT NULL,
    information_request_id     uuid         NOT NULL,
    resolution                 VARCHAR(32)  NOT NULL,
    reason_code                VARCHAR(128) NOT NULL,
    resolved_by_principal_kind VARCHAR(32)  NOT NULL,
    resolved_by_principal_id   uuid         NOT NULL,
    resolved_at                TIMESTAMPTZ  NOT NULL,
    CONSTRAINT information_request_imported_value_discrepancy_resolution_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_imported_value_discrepancy_resolution_fkey
        FOREIGN KEY (discrepancy_id) REFERENCES information_request_imported_value_discrepancy (id),
    CONSTRAINT information_request_imported_value_discrepancy_resolution_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT ux_information_request_imported_value_discrepancy_resolution UNIQUE (discrepancy_id),
    CONSTRAINT ck_information_request_imported_value_discrepancy_resolution CHECK (
        resolution IN ('RESPONSE_STANDS', 'FOLLOW_UP_REQUESTED')
        ),
    CONSTRAINT ck_information_request_imported_value_discrepancy_resolution_reason CHECK (BTRIM(reason_code) <> ''),
    CONSTRAINT ck_information_request_imported_value_discrepancy_resolution_principal CHECK (
        resolved_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                       'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE FUNCTION information_request_imported_value_discrepancy_resolution_scope_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_request_imported_value_discrepancy discrepancy
                   WHERE discrepancy.id = NEW.discrepancy_id
                     AND discrepancy.information_request_id = NEW.information_request_id)
    THEN
        RAISE EXCEPTION 'a resolution belongs to the request of its discrepancy';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_imported_value_discrepancy_resolution_scope_write
    BEFORE INSERT
    ON information_request_imported_value_discrepancy_resolution
    FOR EACH ROW
EXECUTE FUNCTION information_request_imported_value_discrepancy_resolution_scope_guard();

CREATE TRIGGER information_request_imported_value_discrepancy_resolution_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_imported_value_discrepancy_resolution
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TABLE information_request_generated_output
(
    id                         uuid         NOT NULL,
    information_request_id     uuid         NOT NULL,
    package_id                 uuid,
    output_key                 VARCHAR(128) NOT NULL,
    external_reference         VARCHAR(512) NOT NULL,
    content_hash_sha256        VARCHAR(64),
    media_type                 VARCHAR(128),
    produced_by_source         VARCHAR(256) NOT NULL,
    produced_at                TIMESTAMPTZ  NOT NULL,
    recorded_by_principal_kind VARCHAR(32)  NOT NULL,
    recorded_by_principal_id   uuid         NOT NULL,
    recorded_at                TIMESTAMPTZ  NOT NULL,
    CONSTRAINT information_request_generated_output_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_generated_output_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT information_request_generated_output_package_fkey
        FOREIGN KEY (package_id) REFERENCES information_request_submission_package (id),
    CONSTRAINT ck_information_request_generated_output_key CHECK (output_key ~ '^[a-z0-9][a-z0-9._-]{0,127}$'),
    CONSTRAINT ck_information_request_generated_output_text CHECK (
        BTRIM(external_reference) <> '' AND BTRIM(produced_by_source) <> ''
        ),
    CONSTRAINT ck_information_request_generated_output_hash CHECK (
        content_hash_sha256 IS NULL OR content_hash_sha256 ~ '^[0-9a-f]{64}$'
        ),
    CONSTRAINT ck_information_request_generated_output_principal CHECK (
        recorded_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                       'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE INDEX ix_information_request_generated_output_request
    ON information_request_generated_output (information_request_id);

CREATE FUNCTION information_request_generated_output_scope_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NEW.package_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM information_request_submission_package package
        WHERE package.id = NEW.package_id
          AND package.information_request_id = NEW.information_request_id)
    THEN
        RAISE EXCEPTION 'a generated output names a package of its own request';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_generated_output_scope_write
    BEFORE INSERT
    ON information_request_generated_output
    FOR EACH ROW
EXECUTE FUNCTION information_request_generated_output_scope_guard();

CREATE TRIGGER information_request_generated_output_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_generated_output
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE OR REPLACE FUNCTION record_dispose_information_request(disposal_claim_id uuid)
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

    DELETE FROM information_request_imported_value_discrepancy_resolution WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_imported_value_discrepancy_resolution', affected);
    DELETE FROM information_request_imported_value_discrepancy WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_imported_value_discrepancy', affected);
    DELETE FROM information_request_imported_value_decision WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_imported_value_decision', affected);
    DELETE FROM information_request_imported_value WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_imported_value', affected);
    DELETE FROM information_request_connector_exchange WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_connector_exchange', affected);
    DELETE FROM information_request_generated_output WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_generated_output', affected);
    DELETE FROM information_request_fact_recertification_evidence
    WHERE recertification_id IN (SELECT id FROM information_request_fact_recertification WHERE information_request_id = target_request);
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_fact_recertification_evidence', affected);
    DELETE FROM information_request_fact_recertification WHERE information_request_id = target_request;
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_fact_recertification', affected);
    DELETE FROM information_request_accepted_fact_evidence
    WHERE fact_id IN (SELECT id FROM information_request_accepted_fact WHERE source_information_request_id = target_request);
    GET DIAGNOSTICS affected = ROW_COUNT;
    removed := removed || jsonb_build_object('information_request_accepted_fact_evidence', affected);
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
