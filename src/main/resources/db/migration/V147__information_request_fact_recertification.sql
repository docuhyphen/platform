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
                     'ASSIGN_PARTY', 'REVOKE_PARTY', 'SEND_REMINDER')
        );

CREATE TABLE information_request_fact_recertification
(
    id                                 uuid         NOT NULL,
    information_request_id             uuid         NOT NULL,
    information_request_requirement_id uuid         NOT NULL,
    response_id                        uuid         NOT NULL,
    response_revision                  BIGINT       NOT NULL,
    fact_id                            uuid         NOT NULL,
    purpose_key                        VARCHAR(128) NOT NULL,
    policy_basis_key                   VARCHAR(128) NOT NULL,
    value_type                         VARCHAR(32)  NOT NULL,
    canonical_value                    text         NOT NULL,
    source_information_request_id      uuid         NOT NULL,
    source_package_id                  uuid         NOT NULL,
    source_submission_item_id          uuid         NOT NULL,
    source_requirement_id              uuid         NOT NULL,
    source_field_value_revision_id     uuid         NOT NULL,
    source_review_id                   uuid,
    assented_by_principal_kind         VARCHAR(32)  NOT NULL,
    assented_by_principal_id           uuid         NOT NULL,
    assented_by_session_ref            VARCHAR(64),
    assented_at                        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT information_request_fact_recertification_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_fact_recertification_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT information_request_fact_recertification_requirement_fkey
        FOREIGN KEY (information_request_requirement_id) REFERENCES information_request_requirement (id),
    CONSTRAINT information_request_fact_recertification_response_fkey
        FOREIGN KEY (response_id) REFERENCES information_request_response (id),
    CONSTRAINT ck_information_request_fact_recertification_foreign_source
        CHECK (source_information_request_id <> information_request_id),
    CONSTRAINT ck_information_request_fact_recertification_principal CHECK (
        assented_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                       'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE INDEX ix_information_request_fact_recertification_request
    ON information_request_fact_recertification (information_request_id, information_request_requirement_id);

CREATE INDEX ix_information_request_fact_recertification_fact
    ON information_request_fact_recertification (fact_id);

CREATE TABLE information_request_fact_recertification_evidence
(
    id                  uuid NOT NULL,
    recertification_id  uuid NOT NULL,
    evidence_version_id uuid NOT NULL,
    CONSTRAINT information_request_fact_recertification_evidence_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_fact_recertification_evidence_parent_fkey
        FOREIGN KEY (recertification_id) REFERENCES information_request_fact_recertification (id),
    CONSTRAINT ux_information_request_fact_recertification_evidence
        UNIQUE (recertification_id, evidence_version_id)
);

CREATE FUNCTION information_request_fact_recertification_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_request_accepted_fact fact
                   WHERE fact.id = NEW.fact_id
                     AND fact.visibility = 'RESPONDING_PARTIES'
                     AND fact.purpose_key = NEW.purpose_key
                     AND fact.policy_basis_key = NEW.policy_basis_key
                     AND fact.value_type = NEW.value_type
                     AND fact.canonical_value = NEW.canonical_value
                     AND fact.source_information_request_id = NEW.source_information_request_id
                     AND fact.source_package_id = NEW.source_package_id
                     AND fact.source_submission_item_id = NEW.source_submission_item_id
                     AND fact.source_requirement_id = NEW.source_requirement_id
                     AND fact.source_field_value_revision_id = NEW.source_field_value_revision_id
                     AND fact.source_review_id IS NOT DISTINCT FROM NEW.source_review_id
                     AND NOT EXISTS (SELECT 1
                                     FROM information_request_accepted_fact_revocation revocation
                                     WHERE revocation.fact_id = fact.id)
                     AND NOT EXISTS (SELECT 1
                                     FROM information_request_accepted_fact successor
                                     WHERE successor.supersedes_fact_id = fact.id))
    THEN
        RAISE EXCEPTION 'a recertification copies the exact provenance of a current reusable fact';
    END IF;
    IF NOT EXISTS (SELECT 1
                   FROM information_request_response response
                   WHERE response.id = NEW.response_id
                     AND response.information_request_id = NEW.information_request_id
                     AND response.information_request_requirement_id = NEW.information_request_requirement_id
                     AND response.response_revision = NEW.response_revision)
    THEN
        RAISE EXCEPTION 'a recertification names the response revision it wrote';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_fact_recertification_guard_write
    BEFORE INSERT
    ON information_request_fact_recertification
    FOR EACH ROW
EXECUTE FUNCTION information_request_fact_recertification_guard();

CREATE TRIGGER information_request_fact_recertification_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_fact_recertification
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE FUNCTION information_request_fact_recertification_evidence_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_request_fact_recertification recertification
                            JOIN information_request_accepted_fact_evidence reference
                                 ON reference.fact_id = recertification.fact_id
                   WHERE recertification.id = NEW.recertification_id
                     AND reference.evidence_version_id = NEW.evidence_version_id)
    THEN
        RAISE EXCEPTION 'recertified evidence is evidence the reused fact names';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_fact_recertification_evidence_guard_write
    BEFORE INSERT
    ON information_request_fact_recertification_evidence
    FOR EACH ROW
EXECUTE FUNCTION information_request_fact_recertification_evidence_guard();

CREATE TRIGGER information_request_fact_recertification_evidence_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_fact_recertification_evidence
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
