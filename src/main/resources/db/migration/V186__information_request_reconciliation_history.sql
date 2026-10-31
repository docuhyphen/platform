ALTER TABLE information_request_transition
DROP
CONSTRAINT ck_information_request_transition_mutation;

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
                     'RECORD_EXTERNAL_VALUE', 'DECIDE_EXTERNAL_VALUE', 'RECONCILE_IMPORTED_VALUES',
                     'RECORD_GENERATED_OUTPUT',
                     'GRANT_DELEGATED_AUTHORITY', 'REVOKE_DELEGATED_AUTHORITY',
                     'ISSUE_ACCESS_LINK', 'ROTATE_ACCESS_LINK', 'REPLACE_ACCESS_LINK', 'REVOKE_ACCESS_LINK',
                     'REVOKE_ACCESS_SESSION', 'ISSUE_CONTACT_CHALLENGE', 'REFUSE_CONTACT_CHALLENGE',
                     'ISSUE_ACCESS_SESSION', 'LINK_PARTICIPANT_ACCOUNT', 'STEP_UP_ACCESS_SESSION',
                     'START_EXTERNAL_SOURCE_ATTEMPT', 'SCHEDULE_EXTERNAL_SOURCE_RETRY',
                     'RECORD_EXTERNAL_SOURCE_PENDING', 'COMPLETE_EXTERNAL_SOURCE', 'FAIL_EXTERNAL_SOURCE')
        );

CREATE TABLE information_request_fact_supersession
(
    predecessor_fact_id    uuid PRIMARY KEY REFERENCES information_request_accepted_fact (id) ON DELETE CASCADE,
    predecessor_request_id uuid        NOT NULL,
    successor_fact_id      uuid        NOT NULL,
    recorded_at            timestamptz NOT NULL
);

INSERT INTO information_request_fact_supersession
(predecessor_fact_id, predecessor_request_id, successor_fact_id, recorded_at)
SELECT earlier.id, earlier.source_information_request_id, later.id, later.promoted_at
FROM information_request_accepted_fact later
         JOIN information_request_accepted_fact earlier ON earlier.id = later.supersedes_fact_id;

CREATE FUNCTION information_request_fact_supersession_guard() RETURNS TRIGGER AS
    $$
BEGIN
    IF
TG_OP = 'INSERT' AND EXISTS (
        SELECT 1 FROM information_request_accepted_fact earlier
        JOIN information_request_accepted_fact later ON later.supersedes_fact_id = earlier.id
        WHERE earlier.id = NEW.predecessor_fact_id
          AND earlier.source_information_request_id = NEW.predecessor_request_id
          AND later.id = NEW.successor_fact_id
    ) THEN
        RETURN NEW;
END IF;
    IF
TG_OP = 'DELETE' AND EXISTS (
        SELECT 1 FROM record_disposal_claim claim
        WHERE claim.id::text = current_setting('docuhyphen.record_disposal_claim', true)
          AND claim.resource_type = 'INFORMATION_REQUEST'
          AND claim.resource_id = OLD.predecessor_request_id
          AND claim.state = 'OBJECTS_DELETED'
    ) THEN
        RETURN OLD;
END IF;
    RAISE
EXCEPTION 'fact supersession history is append-only';
END;
$$
LANGUAGE plpgsql;

CREATE TRIGGER information_request_fact_supersession_guard_write
    BEFORE INSERT OR
UPDATE OR
DELETE
ON information_request_fact_supersession
    FOR EACH ROW EXECUTE FUNCTION information_request_fact_supersession_guard();

CREATE FUNCTION information_request_fact_supersession_record() RETURNS TRIGGER AS
    $$
BEGIN
    IF
NEW.supersedes_fact_id IS NOT NULL THEN
        INSERT INTO information_request_fact_supersession
            (predecessor_fact_id, predecessor_request_id, successor_fact_id, recorded_at)
SELECT earlier.id, earlier.source_information_request_id, NEW.id, NEW.promoted_at
FROM information_request_accepted_fact earlier
WHERE earlier.id = NEW.supersedes_fact_id;
END IF;
RETURN NEW;
END;
$$
LANGUAGE plpgsql;

CREATE TRIGGER information_request_fact_supersession_record_insert
    AFTER INSERT
    ON information_request_accepted_fact
    FOR EACH ROW EXECUTE FUNCTION information_request_fact_supersession_record();