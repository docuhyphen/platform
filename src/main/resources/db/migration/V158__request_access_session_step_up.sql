ALTER TABLE request_access_session
    ADD COLUMN step_up_code_hash VARCHAR(255),
    ADD COLUMN step_up_code_expires_at TIMESTAMP,
    ADD COLUMN step_up_challenge_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN step_up_failed_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN step_up_locked_until TIMESTAMP,
    ADD COLUMN stepped_up_at TIMESTAMP,
    ADD CONSTRAINT ck_request_access_session_step_up_code
        CHECK ((step_up_code_hash IS NULL) = (step_up_code_expires_at IS NULL)),
    ADD CONSTRAINT ck_request_access_session_step_up_attempts
        CHECK (step_up_challenge_count >= 0 AND step_up_failed_attempts >= 0);

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
                     'RECORD_EXTERNAL_VALUE', 'DECIDE_EXTERNAL_VALUE', 'RECORD_GENERATED_OUTPUT',
                     'GRANT_DELEGATED_AUTHORITY', 'REVOKE_DELEGATED_AUTHORITY',
                     'ISSUE_ACCESS_LINK', 'ROTATE_ACCESS_LINK', 'REPLACE_ACCESS_LINK', 'REVOKE_ACCESS_LINK',
                     'REVOKE_ACCESS_SESSION', 'ISSUE_CONTACT_CHALLENGE', 'REFUSE_CONTACT_CHALLENGE',
                     'ISSUE_ACCESS_SESSION', 'LINK_PARTICIPANT_ACCOUNT', 'STEP_UP_ACCESS_SESSION')
        );
