ALTER TABLE information_request_privacy_request
    ADD COLUMN restriction_id uuid REFERENCES information_request_subject_restriction (id),
    ADD CONSTRAINT ck_information_request_privacy_restriction_kind
        CHECK (restriction_id IS NULL OR request_kind = 'RESTRICTION');
ALTER TABLE information_request_privacy_request DISABLE TRIGGER information_request_privacy_request_guard_write;
UPDATE information_request_privacy_request privacy
SET restriction_id = (SELECT restriction.id
                      FROM information_request_subject_restriction restriction
                      WHERE restriction.owner_kind = privacy.owner_kind
                        AND restriction.owner_id = privacy.owner_id
                        AND restriction.subject_identity_ref_id = privacy.subject_identity_ref_id
                        AND restriction.restricted_at <= COALESCE(privacy.completed_at, privacy.recorded_at)
                        AND (restriction.lifted_at IS NULL OR restriction.lifted_at >= privacy.recorded_at)
                      ORDER BY (restriction.privacy_request_id = privacy.id) DESC, restriction.restricted_at DESC,
                               restriction.id
    LIMIT 1
    )
WHERE privacy.request_kind = 'RESTRICTION' AND privacy.state = 'COMPLETED';
ALTER TABLE information_request_privacy_request ENABLE TRIGGER information_request_privacy_request_guard_write;

ALTER TABLE audit_legal_hold DISABLE TRIGGER audit_legal_hold_guard_write;
UPDATE audit_legal_hold
SET resource_type = upper(btrim(resource_type)),
    resource_id   = CASE
                        WHEN btrim(resource_id) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
                       THEN btrim(resource_id)::uuid::text
                        ELSE btrim(resource_id) END;
ALTER TABLE audit_legal_hold ENABLE TRIGGER audit_legal_hold_guard_write;

ALTER TABLE record_retention_schedule
    ADD CONSTRAINT ck_record_retention_schedule_bounded_days
        CHECK (minimum_retention_days <= 365000 AND
               (disposal_after_days IS NULL OR disposal_after_days <= 365000)) NOT VALID;

ALTER TABLE participant_account_link
    ALTER COLUMN linked_via_share_link_id DROP NOT NULL,
DROP
CONSTRAINT participant_account_link_linked_via_share_link_id_fkey,
    ADD CONSTRAINT participant_account_link_linked_via_share_link_id_fkey
        FOREIGN KEY (linked_via_share_link_id) REFERENCES share_link (id) ON DELETE
SET NULL;

CREATE
OR REPLACE FUNCTION record_preservation_append_only_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    RAISE
EXCEPTION 'record preservation history is append-only';
END;
$$
LANGUAGE plpgsql;

CREATE FUNCTION record_preservation_export_delete_guard()
    RETURNS TRIGGER AS
    $$
DECLARE
bound_export_id uuid := COALESCE(to_jsonb(OLD)->>'export_id', to_jsonb(OLD)->>'id')::uuid;
    bound_claim_id
uuid := NULLIF(current_setting('docuhyphen.record_disposal_claim', true), '')::uuid;
BEGIN
    IF
TG_OP = 'DELETE' AND EXISTS (
        SELECT 1 FROM record_disposal_claim claim
            JOIN record_disposal_claim_scope scope_key ON scope_key.claim_id = claim.id
            JOIN information_request_record_export exported ON exported.id = bound_export_id
        WHERE claim.id = bound_claim_id AND claim.state = 'OBJECTS_DELETED'
          AND claim.resource_type = 'INFORMATION_REQUEST'
          AND scope_key.resource_type = 'INFORMATION_REQUEST_RECORD_EXPORT'
          AND scope_key.resource_id = bound_export_id::text
          AND claim.owner_kind = exported.owner_kind AND claim.owner_id = exported.owner_id
    ) THEN
        RETURN OLD;
END IF;
    RAISE
EXCEPTION 'record preservation history is append-only';
END;
$$
LANGUAGE plpgsql;

DROP TRIGGER information_request_record_export_append_only ON information_request_record_export;
CREATE TRIGGER information_request_record_export_append_only
    BEFORE UPDATE OR
DELETE
ON information_request_record_export
    FOR EACH ROW EXECUTE FUNCTION record_preservation_export_delete_guard();
DROP TRIGGER information_request_record_export_source_append_only ON information_request_record_export_source;
CREATE TRIGGER information_request_record_export_source_append_only
    BEFORE UPDATE OR
DELETE
ON information_request_record_export_source
    FOR EACH ROW EXECUTE FUNCTION record_preservation_export_delete_guard();

ALTER TABLE record_disposal_object
DROP
CONSTRAINT ck_record_disposal_object_locator,
    ADD CONSTRAINT ck_record_disposal_object_locator CHECK (
        (storage_provider IS NULL AND storage_locator_kind IS NULL AND storage_locator IS NULL) OR
        (storage_provider IS NOT NULL AND storage_locator_kind IS NOT NULL AND
         (storage_locator IS NOT NULL OR deleted_at IS NOT NULL))
    );
ALTER TABLE record_disposal_object DISABLE TRIGGER record_disposal_object_guard_write;
UPDATE record_disposal_object
SET storage_locator = NULL
WHERE deleted_at IS NOT NULL;
ALTER TABLE record_disposal_object ENABLE TRIGGER record_disposal_object_guard_write;

CREATE INDEX ix_record_disposal_claim_retry_order ON record_disposal_claim (attempt_count, claimed_at, id) WHERE state <> 'FINALIZED';


CREATE
OR REPLACE FUNCTION record_disposal_scope_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF
TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION 'record preservation history is append-only';
END IF;
    PERFORM
pg_advisory_xact_lock(hashtext('record_preservation'));
    IF
NOT EXISTS (SELECT 1 FROM record_disposal_claim WHERE id = NEW.claim_id AND state = 'CLAIMED' FOR UPDATE) THEN
        RAISE EXCEPTION 'a disposal freezes its scope before deleting objects';
END IF;
    IF
EXISTS (SELECT 1
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
    IF
NEW.resource_type = 'INFORMATION_REQUEST' AND NEW.direct THEN
        IF EXISTS (SELECT 1
                   FROM record_preservation_information_request_records(CAST(NEW.resource_id AS uuid)) owned
                            JOIN audit_legal_hold hold
                                 ON hold.resource_type = owned.resource_type
                                     AND hold.resource_id = owned.resource_id
                            JOIN record_disposal_claim claim ON claim.id = NEW.claim_id
                   WHERE hold.status = 'ACTIVE'
                     AND (hold.owner_kind = 'PLATFORM' OR
                          (hold.owner_kind = claim.owner_kind AND hold.owner_id = claim.owner_id)))
        THEN
            RAISE EXCEPTION 'a held record cannot be claimed for disposal';
END IF;
END IF;
RETURN NEW;
END;
$$
LANGUAGE plpgsql;

CREATE
OR REPLACE FUNCTION record_disposal_object_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF
TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'a disposal object record is never deleted';
END IF;
    IF
NEW.id <> OLD.id
        OR NEW.claim_id <> OLD.claim_id
        OR NEW.object_kind <> OLD.object_kind
        OR NEW.document_id <> OLD.document_id
        OR NEW.document_version_id <> OLD.document_version_id
        OR NEW.storage_provider IS DISTINCT FROM OLD.storage_provider
        OR NEW.storage_locator_kind IS DISTINCT FROM OLD.storage_locator_kind
        OR (NEW.storage_locator IS DISTINCT FROM OLD.storage_locator AND
              NOT (NEW.storage_locator IS NULL AND NEW.deleted_at IS NOT NULL AND NEW.deletion_outcome IS NOT NULL))
        OR NEW.retained <> OLD.retained
        OR NEW.retained_reason IS DISTINCT FROM OLD.retained_reason
        OR OLD.deleted_at IS NOT NULL
    THEN
        RAISE EXCEPTION 'a disposal object records its deletion once and nothing else';
END IF;
RETURN NEW;
END;
$$
LANGUAGE plpgsql;

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

UPDATE participant_account_link
SET linked_via_information_request_id = NULL
WHERE linked_via_information_request_id = target_request;

DELETE
FROM request_access_session
WHERE share_link_id IN (SELECT link.id
                        FROM share_link link
                                 JOIN share grant_row ON grant_row.id = link.share_id
                        WHERE grant_row.resource_type = 'INFORMATION_REQUEST'
                          AND grant_row.resource_id = target_request);
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('request_access_session', affected);
DELETE
FROM share_link
WHERE share_id IN (SELECT id FROM share WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = target_request);
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('share_link', affected);
UPDATE share
SET source_share_id = NULL,
    status          = 'REVOKED'
WHERE source_share_id IN
      (SELECT id FROM share WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = target_request)
  AND NOT (resource_type = 'INFORMATION_REQUEST' AND resource_id = target_request);
DELETE
FROM share
WHERE resource_type = 'INFORMATION_REQUEST'
  AND resource_id = target_request;
GET DIAGNOSTICS affected = ROW_COUNT;
removed
:= removed || jsonb_build_object('share', affected);

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
