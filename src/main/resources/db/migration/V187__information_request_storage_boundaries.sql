DO
$$
DECLARE
column_record RECORD;
BEGIN
FOR column_record IN
SELECT table_name, column_name
FROM information_schema.columns
WHERE table_schema = 'public'
  AND data_type = 'timestamp without time zone'
  AND (table_name LIKE 'information_request_%' OR table_name IN (
                                                                 'request_access_session', 'request_execution_grant',
                                                                 'request_execution_usage_reservation',
                                                                 'share_link', 'command_receipt',
                                                                 'participant_account_link', 'external_participant',
                                                                 'subject_identity_ref',
                                                                 'subject_identity_external_identifier',
                                                                 'subject_identity_transition',
                                                                 'field_definition', 'field_contract', 'field_value',
                                                                 'field_value_revision', 'field_value_set',
                                                                 'schema_definition', 'schema_version',
                                                                 'schema_assignment', 'schema_field_binding'
    ))
ORDER BY table_name, ordinal_position LOOP
        EXECUTE format('ALTER TABLE %I ALTER COLUMN %I TYPE TIMESTAMP(6) WITH TIME ZONE USING %I AT TIME ZONE ''UTC''',
            column_record.table_name, column_record.column_name, column_record.column_name);
END LOOP;
END;
$$;

CREATE INDEX ix_information_request_owner_organization ON information_request (owner_organization_id, state, created_at, id);
CREATE INDEX ix_information_request_owner_user ON information_request (owner_user_id, state, created_at, id);
CREATE INDEX ix_information_request_party_share ON information_request_party (share_id);
CREATE INDEX ix_information_request_party_recipient ON information_request_party (exchange_recipient_id);
CREATE INDEX ix_information_request_superseded_by ON information_request (superseded_by_request_id);
CREATE INDEX ix_participant_account_link_source_request ON participant_account_link (linked_via_information_request_id);
CREATE INDEX ix_information_request_evidence_version_request ON information_request_evidence_version (information_request_id);
CREATE INDEX ix_information_request_submission_evidence_request ON information_request_submission_evidence (information_request_id);
CREATE INDEX ix_information_request_item_correction_request ON information_request_item_correction (information_request_id, recorded_at);
CREATE INDEX ix_information_request_imported_decision_request ON information_request_imported_value_decision (information_request_id);
CREATE INDEX ix_information_request_imported_discrepancy_request ON information_request_imported_value_discrepancy (information_request_id);
CREATE INDEX ix_information_request_imported_resolution_request ON information_request_imported_value_discrepancy_resolution (information_request_id);
CREATE INDEX ix_information_request_withdrawal_request ON information_request_submission_withdrawal (information_request_id);
CREATE INDEX ix_information_request_carry_forward_request ON information_request_carry_forward (information_request_id);
CREATE INDEX ix_information_request_correction_request ON information_request_correction (information_request_id);
CREATE INDEX ix_information_request_review_remediation_request ON information_request_review_remediation (information_request_id);
CREATE INDEX ix_information_request_notice_claim_request ON information_request_notice_claim (information_request_id);
CREATE INDEX ix_information_request_review_assignment_review ON information_request_review_assignment (review_id);
CREATE INDEX ix_domain_event_consumption_event ON domain_event_consumption (event_id);
CREATE INDEX ix_document_version_storage_locator ON document_version USING hash (storage_locator);
CREATE INDEX ix_information_request_subject_restriction_owner ON information_request_subject_restriction (owner_kind, owner_id, restricted_at);
CREATE INDEX ix_information_request_imported_requirement_revision ON information_request_imported_value (requirement_revision_id);
CREATE INDEX ix_information_request_response_archive_response ON information_request_response_archive (response_id);
CREATE INDEX ix_information_request_response_archive_requirement ON information_request_response_archive (information_request_requirement_id);
CREATE INDEX ix_information_request_response_effect_response ON information_request_response_condition_effect (response_id);
CREATE INDEX ix_information_request_response_effect_requirement ON information_request_response_condition_effect (information_request_requirement_id);
CREATE INDEX ix_information_request_response_effect_archive ON information_request_response_condition_effect (archive_id);

ALTER TABLE request_execution_grant
    ADD CONSTRAINT ck_request_execution_grant_revocation
        CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL)) NOT VALID;
