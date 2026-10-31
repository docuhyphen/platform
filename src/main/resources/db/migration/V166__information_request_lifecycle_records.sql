CREATE TABLE exchange_ending_intent
(
    id                                    uuid        NOT NULL,
    exchange_id                           uuid        NOT NULL,
    workflow_instance_id                  uuid        NOT NULL,
    cancel_remaining_information_requests BOOLEAN     NOT NULL,
    requested_by_principal_kind           VARCHAR(32) NOT NULL,
    requested_by_principal_id             uuid        NOT NULL,
    created_at                            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT exchange_ending_intent_pkey PRIMARY KEY (id),
    CONSTRAINT exchange_ending_intent_exchange_fkey
        FOREIGN KEY (exchange_id) REFERENCES exchange (id),
    CONSTRAINT exchange_ending_intent_instance_fkey
        FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instance (id),
    CONSTRAINT ck_exchange_ending_intent_principal CHECK (
        requested_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                        'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE UNIQUE INDEX ux_exchange_ending_intent_instance
    ON exchange_ending_intent (workflow_instance_id);

CREATE INDEX ix_exchange_ending_intent_exchange
    ON exchange_ending_intent (exchange_id);

CREATE TABLE information_request_imported_value_reconciliation
(
    id                         uuid        NOT NULL,
    information_request_id     uuid        NOT NULL,
    recorded_by_principal_kind VARCHAR(32) NOT NULL,
    recorded_by_principal_id   uuid        NOT NULL,
    recorded_at                TIMESTAMPTZ NOT NULL,
    CONSTRAINT information_request_imported_value_reconciliation_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_imported_value_reconciliation_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT uq_information_request_imported_value_reconciliation_request UNIQUE (id, information_request_id),
    CONSTRAINT ck_information_request_imported_value_reconciliation_principal CHECK (
        recorded_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                       'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE INDEX ix_information_request_imported_value_reconciliation_request
    ON information_request_imported_value_reconciliation (information_request_id, recorded_at);

CREATE TABLE information_request_imported_value_reconciliation_item
(
    id                                 uuid        NOT NULL,
    reconciliation_id                  uuid        NOT NULL,
    information_request_id             uuid        NOT NULL,
    item_number                        INTEGER     NOT NULL,
    imported_value_id                  uuid        NOT NULL,
    information_request_requirement_id uuid        NOT NULL,
    outcome                            VARCHAR(32) NOT NULL,
    response_canonical_value           text,
    discrepancy_id                     uuid,
    CONSTRAINT information_request_imported_value_reconciliation_item_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_reconciliation_item_run_fkey
        FOREIGN KEY (reconciliation_id, information_request_id)
            REFERENCES information_request_imported_value_reconciliation (id, information_request_id),
    CONSTRAINT information_request_reconciliation_item_value_fkey
        FOREIGN KEY (imported_value_id) REFERENCES information_request_imported_value (id),
    CONSTRAINT information_request_reconciliation_item_requirement_fkey
        FOREIGN KEY (information_request_requirement_id, information_request_id)
            REFERENCES information_request_requirement (id, information_request_id),
    CONSTRAINT information_request_reconciliation_item_discrepancy_fkey
        FOREIGN KEY (discrepancy_id) REFERENCES information_request_imported_value_discrepancy (id),
    CONSTRAINT ux_information_request_reconciliation_item_number UNIQUE (reconciliation_id, item_number),
    CONSTRAINT ck_information_request_reconciliation_item_position CHECK (item_number >= 1),
    CONSTRAINT ck_information_request_reconciliation_item_outcome CHECK (
        outcome IN ('MATCHES', 'DIFFERS', 'NO_ANSWER', 'NOT_COMPARABLE', 'EXPIRED')
        ),
    CONSTRAINT ck_information_request_reconciliation_item_difference CHECK (
        (outcome = 'DIFFERS') = (discrepancy_id IS NOT NULL) AND
        (outcome = 'DIFFERS') = (response_canonical_value IS NOT NULL)
        )
);

CREATE INDEX ix_information_request_reconciliation_item_request
    ON information_request_imported_value_reconciliation_item (information_request_id, imported_value_id);

CREATE FUNCTION information_request_reconciliation_item_scope_guard()
    RETURNS TRIGGER AS
    $$
BEGIN
    IF
NOT EXISTS (SELECT 1
                   FROM information_request_imported_value imported
                   WHERE imported.id = NEW.imported_value_id
                     AND imported.information_request_id = NEW.information_request_id
                     AND imported.information_request_requirement_id = NEW.information_request_requirement_id)
    THEN
        RAISE EXCEPTION 'a reconciliation outcome names an imported value of its own request';
END IF;
    IF
NEW.discrepancy_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM information_request_imported_value_discrepancy discrepancy
        WHERE discrepancy.id = NEW.discrepancy_id
          AND discrepancy.imported_value_id = NEW.imported_value_id
          AND discrepancy.information_request_id = NEW.information_request_id)
    THEN
        RAISE EXCEPTION 'a reconciliation outcome names a discrepancy of its own imported value';
END IF;
RETURN NEW;
END;
$$
LANGUAGE plpgsql;

CREATE TRIGGER information_request_reconciliation_item_scope_write
    BEFORE INSERT
    ON information_request_imported_value_reconciliation_item
    FOR EACH ROW
    EXECUTE FUNCTION information_request_reconciliation_item_scope_guard();

CREATE TRIGGER information_request_imported_value_reconciliation_append_only
    BEFORE UPDATE OR
DELETE
ON information_request_imported_value_reconciliation
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER information_request_reconciliation_item_append_only
    BEFORE UPDATE OR
DELETE
ON information_request_imported_value_reconciliation_item
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TABLE information_request_lineage_source_package
(
    id                   uuid        NOT NULL,
    lineage_id           uuid        NOT NULL,
    successor_request_id uuid        NOT NULL,
    source_request_id    uuid        NOT NULL,
    package_id           uuid        NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_lineage_source_package_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_lineage_source_package_lineage_fkey
        FOREIGN KEY (lineage_id, successor_request_id)
            REFERENCES information_request_lineage (id, successor_request_id),
    CONSTRAINT information_request_lineage_source_package_package_fkey
        FOREIGN KEY (package_id, source_request_id)
            REFERENCES information_request_submission_package (id, information_request_id),
    CONSTRAINT information_request_lineage_source_package_unique UNIQUE (lineage_id, package_id)
);

CREATE INDEX ix_information_request_lineage_source_package_successor
    ON information_request_lineage_source_package (successor_request_id);

CREATE INDEX ix_information_request_lineage_source_package_package
    ON information_request_lineage_source_package (package_id);

INSERT INTO information_request_lineage_source_package (id, lineage_id, successor_request_id, source_request_id,
                                                        package_id,
                                                        created_at)
SELECT gen_random_uuid(),
       lineage.id,
       lineage.successor_request_id,
       lineage.source_request_id,
       lineage.source_package_id,
       lineage.created_at
FROM information_request_lineage lineage
WHERE lineage.source_package_id IS NOT NULL;

CREATE FUNCTION information_request_lineage_source_package_guard()
    RETURNS TRIGGER AS
    $$
BEGIN
    IF
NOT EXISTS (SELECT 1
                   FROM information_request_lineage lineage
                   WHERE lineage.id = NEW.lineage_id
                     AND lineage.successor_request_id = NEW.successor_request_id
                     AND lineage.source_request_id = NEW.source_request_id)
    THEN
        RAISE EXCEPTION 'a preserved package belongs to the request its follow-up follows';
END IF;
RETURN NEW;
END;
$$
LANGUAGE plpgsql;

CREATE TRIGGER information_request_lineage_source_package_write
    BEFORE INSERT
    ON information_request_lineage_source_package
    FOR EACH ROW
    EXECUTE FUNCTION information_request_lineage_source_package_guard();

CREATE TRIGGER information_request_lineage_source_package_append_only
    BEFORE UPDATE OR
DELETE
ON information_request_lineage_source_package
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE
OR REPLACE FUNCTION information_request_carry_forward_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF
NOT EXISTS (
        SELECT 1
        FROM information_request_lineage_source_package preserved
                 JOIN information_request_submission_item item ON item.package_id = preserved.package_id
        WHERE preserved.lineage_id = NEW.lineage_id
          AND preserved.package_id = NEW.source_package_id
          AND item.id = NEW.source_item_id
    ) THEN
        RAISE EXCEPTION 'a carry-forward decision names an item of the packages its follow-up request preserves';
END IF;

RETURN NEW;
END;
$$
LANGUAGE plpgsql;

ALTER TABLE request_execution_grant
    ADD COLUMN policy_version VARCHAR(32) NOT NULL DEFAULT '1',
    ADD COLUMN entitlement_source VARCHAR(16) NOT NULL DEFAULT 'PAID',
    ADD COLUMN trial_grant_id uuid,
    ADD COLUMN granted_features VARCHAR(64)[] NOT NULL DEFAULT ARRAY ['INFORMATION_REQUESTS']::VARCHAR(64)[],
    ADD COLUMN continuation_actions VARCHAR(64)[] NOT NULL DEFAULT ARRAY ['RESPOND', 'SUBMIT', 'ATTEST', 'REVIEW',
        'ASSIGN_PARTY_WITHIN_CAP', 'UPLOAD_WITHIN_ALLOWANCE', 'NON_EXPANDING_AMENDMENT', 'SEND_REMINDER', 'CANCEL',
        'SUPERSEDE']::VARCHAR(64)[];

UPDATE request_execution_grant
SET entitlement_source = CASE
                             WHEN enforcement_mode <> 'ENFORCE' THEN 'UNENFORCED'
                             WHEN subscription_status = 'TRIALING' THEN 'TRIAL'
                             ELSE 'PAID'
    END,
    granted_features   = ARRAY['INFORMATION_REQUESTS', 'BUSINESS_FIELDS_AND_SCHEMAS']:: VARCHAR (64)[];

UPDATE request_execution_grant execution_grant
SET trial_grant_id = (SELECT trial.id
                      FROM subscription_trial_grant trial
                      WHERE trial.owner_type = execution_grant.owner_type
                        AND trial.owner_id =
                            COALESCE(execution_grant.owner_organization_id, execution_grant.owner_user_id)
                        AND trial.started_at <= execution_grant.issued_at
                      ORDER BY trial.started_at DESC
    LIMIT 1)
WHERE execution_grant.entitlement_source = 'TRIAL';

ALTER TABLE request_execution_grant
    ADD CONSTRAINT request_execution_grant_trial_fkey
        FOREIGN KEY (trial_grant_id) REFERENCES subscription_trial_grant (id),
    ADD CONSTRAINT ck_request_execution_grant_policy_version CHECK (BTRIM(policy_version) <> ''),
    ADD CONSTRAINT ck_request_execution_grant_entitlement_source CHECK (
        entitlement_source IN ('PAID', 'TRIAL', 'OVERRIDE', 'UNENFORCED')
        ),
    ADD CONSTRAINT ck_request_execution_grant_trial_source CHECK (
        trial_grant_id IS NULL OR entitlement_source = 'TRIAL'
        ),
    ADD CONSTRAINT ck_request_execution_grant_trial_reference CHECK (
        entitlement_source <> 'TRIAL' OR subscription_status = 'TRIALING' OR trial_grant_id IS NOT NULL
        ),
    ADD CONSTRAINT ck_request_execution_grant_features CHECK (
        'INFORMATION_REQUESTS' = ANY (granted_features) AND
        granted_features <@ ARRAY ['INFORMATION_REQUESTS', 'BUSINESS_FIELDS_AND_SCHEMAS']::VARCHAR(64)[]
        ),
    ADD CONSTRAINT ck_request_execution_grant_continuation_actions CHECK (
        continuation_actions <@ ARRAY ['RESPOND', 'SUBMIT', 'ATTEST', 'REVIEW', 'ASSIGN_PARTY_WITHIN_CAP',
            'UPLOAD_WITHIN_ALLOWANCE', 'NON_EXPANDING_AMENDMENT', 'SEND_REMINDER', 'CANCEL', 'SUPERSEDE']::VARCHAR(64)[]
        );

CREATE
OR REPLACE FUNCTION request_execution_grant_frozen() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF
ROW (NEW.id, NEW.request_id, NEW.owner_type, NEW.owner_organization_id, NEW.owner_user_id,
            NEW.plan_code, NEW.subscription_status, NEW.enforcement_mode, NEW.trial_expires_at,
            NEW.mutation_allowance_expires_at, NEW.acting_party_cap, NEW.evidence_file_allowance,
            NEW.evidence_byte_allowance, NEW.issued_at, NEW.created_at, NEW.policy_version,
            NEW.entitlement_source, NEW.trial_grant_id, NEW.granted_features, NEW.continuation_actions)
        IS DISTINCT FROM
       ROW (OLD.id, OLD.request_id, OLD.owner_type, OLD.owner_organization_id, OLD.owner_user_id,
            OLD.plan_code, OLD.subscription_status, OLD.enforcement_mode, OLD.trial_expires_at,
            OLD.mutation_allowance_expires_at, OLD.acting_party_cap, OLD.evidence_file_allowance,
            OLD.evidence_byte_allowance, OLD.issued_at, OLD.created_at, OLD.policy_version,
            OLD.entitlement_source, OLD.trial_grant_id, OLD.granted_features, OLD.continuation_actions)
    THEN
        RAISE EXCEPTION 'an execution grant keeps the position it was issued under'
            USING ERRCODE = 'check_violation';
END IF;
    IF
OLD.revoked_at IS NOT NULL
        AND ROW (NEW.revoked_at, NEW.revoked_reason) IS DISTINCT FROM ROW (OLD.revoked_at, OLD.revoked_reason)
    THEN
        RAISE EXCEPTION 'an execution grant is revoked once'
            USING ERRCODE = 'check_violation';
END IF;
RETURN NEW;
END;
$$;

CREATE
OR REPLACE FUNCTION record_dispose_information_request(disposal_claim_id uuid)
    RETURNS TEXT AS
$$
DECLARE
claim             record_disposal_claim%ROWTYPE;
    target_request
uuid;
    target_assignment
uuid;
    target_definition
uuid;
    export_ids
uuid[];
    removed
jsonb := '{}'::jsonb;
    affected
BIGINT;
    package_count
BIGINT;
    request_count
BIGINT;
BEGIN
SELECT *
INTO claim
FROM record_disposal_claim
WHERE id = disposal_claim_id FOR UPDATE;
IF
NOT FOUND THEN
        RAISE EXCEPTION 'a disposal names an existing claim';
END IF;
    IF
claim.state <> 'OBJECTS_DELETED' OR claim.resource_type <> 'INFORMATION_REQUEST' THEN
        RAISE EXCEPTION 'a record is finalized only after its claimed objects are deleted';
END IF;
    target_request
:= claim.resource_id;
    PERFORM
set_config('docuhyphen.record_disposal_claim', disposal_claim_id::text, true);

SELECT id
INTO target_assignment
FROM schema_assignment
WHERE resource_type = 'INFORMATION_REQUEST'
  AND resource_id = target_request;
SELECT id
INTO target_definition
FROM information_request_template_definition
WHERE origin_request_id = target_request;
export_ids
:= ARRAY(SELECT export_id
                        FROM information_request_record_export_source
                        WHERE information_request_id = target_request
                        UNION
                        SELECT id
                        FROM information_request_record_export
                        WHERE information_request_id = target_request);

DELETE
FROM information_request_record_export_source
WHERE export_id = ANY (export_ids);
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_record_export_source', affected);
DELETE
FROM information_request_record_export
WHERE id = ANY (export_ids);
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_record_export', affected);

DELETE
FROM information_request_notice_sequence_allocation
WHERE outbound_notice_id IN
      (SELECT id FROM information_request_outbound_notice WHERE information_request_id = target_request);
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_notice_sequence_allocation', affected);
DELETE
FROM information_request_notice_delivery_attempt
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_notice_delivery_attempt', affected);
DELETE
FROM information_request_outbound_notice
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_outbound_notice', affected);
DELETE
FROM information_request_notice_claim
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_notice_claim', affected);
DELETE
FROM information_request_notice_intent
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_notice_intent', affected);

DELETE
FROM information_request_clock_event
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_clock_event', affected);
DELETE
FROM information_request_clock
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_clock', affected);

DELETE
FROM information_request_imported_value_reconciliation_item
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_imported_value_reconciliation_item', affected);
DELETE
FROM information_request_imported_value_reconciliation
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_imported_value_reconciliation', affected);
DELETE
FROM information_request_imported_value_discrepancy_resolution
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_imported_value_discrepancy_resolution', affected);
DELETE
FROM information_request_imported_value_discrepancy
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_imported_value_discrepancy', affected);
DELETE
FROM information_request_imported_value_decision
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_imported_value_decision', affected);
DELETE
FROM information_request_imported_value
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_imported_value', affected);
DELETE
FROM information_request_connector_exchange
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_connector_exchange', affected);
DELETE
FROM information_request_generated_output
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_generated_output', affected);
DELETE
FROM information_request_fact_recertification_evidence
WHERE recertification_id IN
      (SELECT id FROM information_request_fact_recertification WHERE information_request_id = target_request);
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_fact_recertification_evidence', affected);
DELETE
FROM information_request_fact_recertification
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_fact_recertification', affected);
DELETE
FROM information_request_accepted_fact_evidence
WHERE
    fact_id IN (SELECT id FROM information_request_accepted_fact WHERE source_information_request_id = target_request);
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_accepted_fact_evidence', affected);
DELETE
FROM information_request_accepted_fact_revocation
WHERE
    fact_id IN (SELECT id FROM information_request_accepted_fact WHERE source_information_request_id = target_request);
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_accepted_fact_revocation', affected);
DELETE
FROM information_request_accepted_fact
WHERE source_information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_accepted_fact', affected);
DELETE
FROM information_request_business_decision
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_business_decision', affected);

DELETE
FROM information_request_review_remediation
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_review_remediation', affected);
DELETE
FROM information_request_review_comment
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_review_comment', affected);
DELETE
FROM information_request_correction_evidence
WHERE correction_id IN (SELECT id FROM information_request_correction WHERE information_request_id = target_request);
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_correction_evidence', affected);
DELETE
FROM information_request_correction_item
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_correction_item', affected);
DELETE
FROM information_request_correction
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_correction', affected);
DELETE
FROM information_request_review_finding
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_review_finding', affected);
DELETE
FROM information_request_review_decision
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_review_decision', affected);
DELETE
FROM information_request_review_draft_item
WHERE review_id IN (SELECT id FROM information_request_review WHERE information_request_id = target_request);
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_review_draft_item', affected);
DELETE
FROM information_request_review_assignment
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_review_assignment', affected);
DELETE
FROM information_request_review
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_review', affected);

DELETE
FROM information_request_carry_forward
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_carry_forward', affected);
DELETE
FROM information_request_lineage_source_package
WHERE successor_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_lineage_source_package', affected);
DELETE
FROM information_request_lineage
WHERE successor_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_lineage', affected);
DELETE
FROM information_request_refresh_rule
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_refresh_rule', affected);
DELETE
FROM information_request_recurrence
WHERE origin_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_recurrence', affected);

DELETE
FROM information_request_submission_package_attestation
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_submission_package_attestation', affected);
DELETE
FROM information_request_submission_attestation
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_submission_attestation', affected);
DELETE
FROM information_request_submission_withdrawal
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_submission_withdrawal', affected);
DELETE
FROM information_request_submission_supporting_link
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_submission_supporting_link', affected);
DELETE
FROM information_request_submission_evidence
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_submission_evidence', affected);
DELETE
FROM information_request_submission_item
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_submission_item', affected);

DELETE
FROM information_request_supporting_evidence_link
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_supporting_evidence_link', affected);
DELETE
FROM information_request_evidence_assessment
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_evidence_assessment', affected);
DELETE
FROM information_request_evidence_version
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_evidence_version', affected);
DELETE
FROM information_request_evidence_artifact
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_evidence_artifact', affected);

DELETE
FROM information_request_amendment_change
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_amendment_change', affected);
DELETE
FROM information_request_response
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_response', affected);
DELETE
FROM information_request_amendment
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_amendment', affected);
DELETE
FROM information_request_group_occurrence
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_group_occurrence', affected);

DELETE
FROM information_request_requirement_current
WHERE information_request_requirement_id IN
      (SELECT id FROM information_request_requirement WHERE information_request_id = target_request);
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_requirement_current', affected);
DELETE
FROM information_request_delegated_authority
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_delegated_authority', affected);
DELETE
FROM information_request_requirement_revision
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_requirement_revision', affected);
DELETE
FROM information_request_requirement
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_requirement', affected);
DELETE
FROM information_request_document_placeholder
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_document_placeholder', affected);
DELETE
FROM information_request_transition
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_transition', affected);
DELETE
FROM information_request_party
WHERE information_request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_party', affected);
DELETE
FROM request_execution_usage_reservation
WHERE grant_id IN (SELECT id FROM request_execution_grant WHERE request_id = target_request);
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('request_execution_usage_reservation', affected);
DELETE
FROM request_execution_grant
WHERE request_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('request_execution_grant', affected);

    IF
target_assignment IS NOT NULL THEN
DELETE
FROM field_value_revision
WHERE schema_assignment_id = target_assignment;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('field_value_revision', affected);
DELETE
FROM field_value_selection
WHERE field_value_id IN (SELECT id FROM field_value WHERE schema_assignment_id = target_assignment);
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('field_value_selection', affected);
DELETE
FROM field_value
WHERE schema_assignment_id = target_assignment;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('field_value', affected);
DELETE
FROM field_value_set
WHERE schema_assignment_id = target_assignment;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('field_value_set', affected);
DELETE
FROM schema_assignment
WHERE id = target_assignment;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('schema_assignment', affected);
END IF;

DELETE
FROM document_version
WHERE
    id IN (SELECT document_version_id FROM record_disposal_object WHERE claim_id = disposal_claim_id AND NOT retained);
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('document_version', affected);
DELETE
FROM document
WHERE id IN (SELECT document_id FROM record_disposal_object WHERE claim_id = disposal_claim_id AND NOT retained)
  AND NOT EXISTS (SELECT 1 FROM document_version remaining WHERE remaining.document_id = document.id);
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('document', affected);

WITH packages AS (
DELETE
FROM information_request_submission_package
WHERE information_request_id = target_request RETURNING 1)
    , requests AS (
DELETE
FROM information_request
WHERE id = target_request RETURNING 1)
SELECT (SELECT COUNT(*) FROM packages), (SELECT COUNT(*) FROM requests)
INTO package_count, request_count;
removed
:= removed || jsonb_build_object('information_request_submission_package', package_count,
                                             'information_request', request_count);

    IF
target_definition IS NOT NULL THEN
DELETE
FROM information_request_template_attestation_role
WHERE template_version_id IN
      (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
DELETE
FROM information_request_template_condition_predicate_literal
WHERE template_version_id IN
      (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
DELETE
FROM information_request_template_evidence_accepted_value
WHERE template_version_id IN
      (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
DELETE
FROM information_request_template_review_stage_section
WHERE template_version_id IN
      (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
DELETE
FROM information_request_template_attestation_policy
WHERE template_version_id IN
      (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
DELETE
FROM information_request_template_condition_predicate
WHERE template_version_id IN
      (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
DELETE
FROM information_request_template_evidence_policy
WHERE template_version_id IN
      (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
DELETE
FROM information_request_template_binding_disposition
WHERE template_version_id IN
      (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
DELETE
FROM information_request_template_binding_evidence_link
WHERE template_version_id IN
      (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
DELETE
FROM information_request_template_binding_substitute
WHERE template_version_id IN
      (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
DELETE
FROM information_request_template_condition_rule
WHERE template_version_id IN
      (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
DELETE
FROM information_request_template_requirement_binding
WHERE template_version_id IN
      (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
DELETE
FROM information_request_template_review_stage
WHERE template_version_id IN
      (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
DELETE
FROM information_request_template_requirement_group
WHERE template_version_id IN
      (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
DELETE
FROM information_request_template_section
WHERE template_version_id IN
      (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
DELETE
FROM information_request_template_version_capability
WHERE template_version_id IN
      (SELECT id FROM information_request_template_version WHERE template_definition_id = target_definition);
DELETE
FROM information_request_template_version
WHERE template_definition_id = target_definition;
DELETE
FROM information_request_template_requirement
WHERE template_definition_id = target_definition;
DELETE
FROM information_request_template_definition
WHERE id = target_definition;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('information_request_template_definition', affected);
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
       removed::text, (SELECT COUNT(*) FROM record_disposal_object WHERE claim_id = claim.id AND NOT retained),
       (SELECT COUNT(*) FROM record_disposal_object WHERE claim_id = claim.id AND retained),
       CURRENT_TIMESTAMP;

UPDATE record_disposal_claim
SET state          = 'FINALIZED',
    finalized_at   = CURRENT_TIMESTAMP,
    claim_revision = claim_revision + 1
WHERE id = claim.id;

PERFORM
set_config('docuhyphen.record_disposal_claim', '', true);
RETURN removed::text;
END;
$$
LANGUAGE plpgsql;
