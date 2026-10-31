ALTER TABLE information_request_fact_recertification
    ADD CONSTRAINT information_request_recertification_fact_fkey
        FOREIGN KEY (fact_id) REFERENCES information_request_accepted_fact (id),
    ADD CONSTRAINT information_request_recertification_source_request_fkey
        FOREIGN KEY (source_information_request_id) REFERENCES information_request (id),
    ADD CONSTRAINT information_request_recertification_source_package_fkey
        FOREIGN KEY (source_package_id) REFERENCES information_request_submission_package (id),
    ADD CONSTRAINT information_request_recertification_source_item_fkey
        FOREIGN KEY (source_submission_item_id) REFERENCES information_request_submission_item (id),
    ADD CONSTRAINT information_request_recertification_source_requirement_fkey
        FOREIGN KEY (source_requirement_id) REFERENCES information_request_requirement (id),
    ADD CONSTRAINT information_request_recertification_source_revision_fkey
        FOREIGN KEY (source_field_value_revision_id) REFERENCES field_value_revision (id),
    ADD CONSTRAINT information_request_recertification_source_review_fkey
        FOREIGN KEY (source_review_id) REFERENCES information_request_review (id);

ALTER TABLE information_request_fact_recertification_evidence
    ADD CONSTRAINT information_request_recertification_evidence_version_fkey
        FOREIGN KEY (evidence_version_id) REFERENCES information_request_evidence_version (id);

CREATE INDEX ix_information_request_recertification_source_request
    ON information_request_fact_recertification (source_information_request_id);

CREATE INDEX ix_information_request_recertification_source_package
    ON information_request_fact_recertification (source_package_id);

CREATE INDEX ix_information_request_recertification_source_item
    ON information_request_fact_recertification (source_submission_item_id);

CREATE INDEX ix_information_request_recertification_source_requirement
    ON information_request_fact_recertification (source_requirement_id);

CREATE INDEX ix_information_request_recertification_source_revision
    ON information_request_fact_recertification (source_field_value_revision_id);

CREATE INDEX ix_information_request_recertification_source_review
    ON information_request_fact_recertification (source_review_id);

CREATE INDEX ix_information_request_recertification_evidence_version
    ON information_request_fact_recertification_evidence (evidence_version_id);

CREATE
OR REPLACE FUNCTION record_disposal_reference_guard()
    RETURNS TRIGGER AS
$$
DECLARE
candidate      uuid := (to_jsonb(NEW) ->> TG_ARGV[1])::uuid;
    owning_request
uuid;
    gained
BOOLEAN;
BEGIN
    IF
candidate IS NULL THEN
        RETURN NEW;
END IF;
    IF
TG_OP = 'INSERT' THEN
        gained := TRUE;
ELSE
        gained := (to_jsonb(OLD) ->> TG_ARGV[1]) IS DISTINCT FROM (to_jsonb(NEW) ->> TG_ARGV[1]);
END IF;

    IF
TG_ARGV[0] = 'DOCUMENT_VERSION' THEN
        IF gained THEN
            PERFORM 1 FROM document_version referenced WHERE referenced.id = candidate FOR KEY SHARE;
END IF;
        IF
EXISTS (SELECT 1
                   FROM record_disposal_object object
                            JOIN record_disposal_claim claim ON claim.id = object.claim_id
                   WHERE claim.state <> 'FINALIZED'
                     AND NOT object.retained
                     AND object.document_version_id = candidate)
        THEN
            RAISE EXCEPTION 'a record under disposal cannot gain a reference';
END IF;
RETURN NEW;
END IF;

    IF
TG_ARGV[0] = 'DOCUMENT' THEN
        IF gained THEN
            PERFORM 1 FROM document referenced WHERE referenced.id = candidate FOR KEY SHARE;
END IF;
        IF
EXISTS (SELECT 1
                   FROM record_disposal_object object
                            JOIN record_disposal_claim claim ON claim.id = object.claim_id
                   WHERE claim.state <> 'FINALIZED'
                     AND NOT object.retained
                     AND object.document_id = candidate)
        THEN
            RAISE EXCEPTION 'a record under disposal cannot gain a reference';
END IF;
RETURN NEW;
END IF;

    owning_request
:= CASE TG_ARGV[0]
                          WHEN 'INFORMATION_REQUEST' THEN candidate
                          WHEN 'SUBMISSION_ITEM' THEN (SELECT item.information_request_id
                                                       FROM information_request_submission_item item
                                                       WHERE item.id = candidate)
                          WHEN 'ACCEPTED_FACT' THEN (SELECT fact.source_information_request_id
                                                     FROM information_request_accepted_fact fact
                                                     WHERE fact.id = candidate)
                          WHEN 'EVIDENCE_ASSESSMENT' THEN (SELECT assessment.information_request_id
                                                           FROM information_request_evidence_assessment assessment
                                                           WHERE assessment.id = candidate)
                          WHEN 'EVIDENCE_VERSION' THEN (SELECT evidence.information_request_id
                                                        FROM information_request_evidence_version evidence
                                                        WHERE evidence.id = candidate)
END;
    IF
owning_request IS NULL THEN
        RETURN NEW;
END IF;
    IF
gained THEN
        PERFORM 1 FROM information_request referenced WHERE referenced.id = owning_request FOR KEY SHARE;
END IF;
    IF
EXISTS (SELECT 1
               FROM record_disposal_claim claim
               WHERE claim.state <> 'FINALIZED'
                 AND claim.resource_type = 'INFORMATION_REQUEST'
                 AND claim.resource_id = owning_request)
    THEN
        RAISE EXCEPTION 'a record under disposal cannot gain a reference';
END IF;
RETURN NEW;
END;
$$
LANGUAGE plpgsql;

CREATE TRIGGER record_disposal_reference_fact_recertification
    BEFORE INSERT
    ON information_request_fact_recertification
    FOR EACH ROW
    EXECUTE FUNCTION record_disposal_reference_guard('INFORMATION_REQUEST', 'source_information_request_id');

CREATE TRIGGER record_disposal_reference_recertification_evidence
    BEFORE INSERT
    ON information_request_fact_recertification_evidence
    FOR EACH ROW
    EXECUTE FUNCTION record_disposal_reference_guard('EVIDENCE_VERSION', 'evidence_version_id');

CREATE FUNCTION record_disposal_lock_information_request_objects(target_request uuid)
    RETURNS INTEGER AS
    $$
DECLARE
locked_requests  INTEGER;
    locked_documents
INTEGER;
    locked_versions
INTEGER;
BEGIN
SELECT COUNT(*)
INTO locked_requests
FROM (SELECT request.id
      FROM information_request request
      WHERE request.id = target_request
          FOR
      UPDATE) locked;

SELECT COUNT(*)
INTO locked_documents
FROM (SELECT stored_document.id
      FROM document stored_document
      WHERE stored_document.id IN (SELECT stored_version.document_id
                                   FROM document_version stored_version
                                   WHERE stored_version.id IN (SELECT evidence.document_version_id
                                                               FROM information_request_evidence_version evidence
                                                               WHERE evidence.information_request_id = target_request
                                                                 AND evidence.document_version_id IS NOT NULL
                                                               UNION
                                                               SELECT submitted.document_version_id
                                                               FROM information_request_submission_evidence submitted
                                                               WHERE submitted.information_request_id = target_request
                                                                 AND submitted.document_version_id IS NOT NULL))
      ORDER BY stored_document.id
          FOR
      UPDATE) locked;

SELECT COUNT(*)
INTO locked_versions
FROM (SELECT stored_version.id
      FROM document_version stored_version
      WHERE stored_version.id IN (SELECT evidence.document_version_id
                                  FROM information_request_evidence_version evidence
                                  WHERE evidence.information_request_id = target_request
                                    AND evidence.document_version_id IS NOT NULL
                                  UNION
                                  SELECT submitted.document_version_id
                                  FROM information_request_submission_evidence submitted
                                  WHERE submitted.information_request_id = target_request
                                    AND submitted.document_version_id IS NOT NULL)
      ORDER BY stored_version.id
          FOR
      UPDATE) locked;

RETURN locked_requests + locked_documents + locked_versions;
END;
$$
LANGUAGE plpgsql;
