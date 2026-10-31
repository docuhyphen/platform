CREATE FUNCTION record_preservation_information_request_records(target_request uuid)
    RETURNS TABLE
            (
                resource_type VARCHAR,
                resource_id   VARCHAR
            ) AS
$$
SELECT CAST('INFORMATION_REQUEST_SUBMISSION_PACKAGE' AS VARCHAR), CAST(submitted.id AS VARCHAR)
FROM information_request_submission_package submitted
WHERE submitted.information_request_id = target_request
UNION ALL
SELECT CAST('INFORMATION_REQUEST_EVIDENCE_ARTIFACT' AS VARCHAR), CAST(artifact.id AS VARCHAR)
FROM information_request_evidence_artifact artifact
WHERE artifact.information_request_id = target_request
UNION ALL
SELECT CAST('INFORMATION_REQUEST_EVIDENCE_VERSION' AS VARCHAR), CAST(evidence.id AS VARCHAR)
FROM information_request_evidence_version evidence
WHERE evidence.information_request_id = target_request
UNION ALL
SELECT CAST('INFORMATION_REQUEST_OUTBOUND_NOTICE' AS VARCHAR), CAST(notice.id AS VARCHAR)
FROM information_request_outbound_notice notice
WHERE notice.information_request_id = target_request
UNION ALL
SELECT CAST('INFORMATION_REQUEST_RECORD_EXPORT' AS VARCHAR), CAST(exported.id AS VARCHAR)
FROM (SELECT source.export_id AS id
      FROM information_request_record_export_source source
      WHERE source.information_request_id = target_request
      UNION
      SELECT record_export.id
      FROM information_request_record_export record_export
      WHERE record_export.information_request_id = target_request) exported $$ LANGUAGE sql STABLE;

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
OR REPLACE FUNCTION audit_legal_hold_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF
TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'a record preservation hold is never deleted';
END IF;
    PERFORM
pg_advisory_xact_lock(hashtext('record_preservation'));
    IF
TG_OP = 'UPDATE' THEN
        IF OLD.status = 'RELEASED' THEN
            RAISE EXCEPTION 'a released record preservation hold is immutable';
END IF;
        IF
NEW.id <> OLD.id
            OR NEW.owner_kind <> OLD.owner_kind
            OR NEW.owner_id IS DISTINCT FROM OLD.owner_id
            OR NEW.resource_type <> OLD.resource_type
            OR NEW.resource_id <> OLD.resource_id
            OR NEW.reason <> OLD.reason
            OR NEW.case_reference IS DISTINCT FROM OLD.case_reference
            OR NEW.effective_from <> OLD.effective_from
            OR NEW.placed_by_principal_kind <> OLD.placed_by_principal_kind
            OR NEW.placed_by_principal_id <> OLD.placed_by_principal_id
            OR NEW.placed_at <> OLD.placed_at
            OR NEW.created_at <> OLD.created_at
        THEN
            RAISE EXCEPTION 'a record preservation hold keeps the identity it was placed with';
END IF;
        IF
NEW.hold_revision <> OLD.hold_revision + 1 THEN
            RAISE EXCEPTION 'a record preservation hold revision advances by one';
END IF;
END IF;
    IF
NEW.status = 'ACTIVE' AND EXISTS (SELECT 1
                                         FROM record_disposal_claim_scope scope_key
                                                  JOIN record_disposal_claim claim ON claim.id = scope_key.claim_id
                                         WHERE claim.state <> 'FINALIZED'
                                           AND scope_key.resource_type = NEW.resource_type
                                           AND scope_key.resource_id = NEW.resource_id
                                           AND (scope_key.direct OR NEW.scope = 'DESCENDANTS_AND_REFERENCES')
                                           AND (NEW.owner_kind = 'PLATFORM' OR
                                                (claim.owner_kind = NEW.owner_kind AND claim.owner_id = NEW.owner_id)))
    THEN
        RAISE EXCEPTION 'a record under disposal cannot be placed on hold';
END IF;
    IF
NEW.status = 'ACTIVE' AND EXISTS (SELECT 1
                                         FROM record_disposal_claim claim
                                                  JOIN record_disposal_claim_scope scope_key
                                                       ON scope_key.claim_id = claim.id
                                                           AND scope_key.resource_type = 'INFORMATION_REQUEST'
                                                           AND scope_key.direct
                                                  CROSS JOIN LATERAL record_preservation_information_request_records(
                                                 CAST(scope_key.resource_id AS uuid)) owned
                                         WHERE claim.state <> 'FINALIZED'
                                           AND owned.resource_type = NEW.resource_type
                                           AND owned.resource_id = NEW.resource_id
                                           AND (NEW.owner_kind = 'PLATFORM' OR
                                                (claim.owner_kind = NEW.owner_kind AND claim.owner_id = NEW.owner_id)))
    THEN
        RAISE EXCEPTION 'a record under disposal cannot be placed on hold';
END IF;
RETURN NEW;
END;
$$
LANGUAGE plpgsql;
