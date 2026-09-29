ALTER TABLE information_request_transition
    DROP CONSTRAINT ck_information_request_transition_mutation;

ALTER TABLE information_request_transition
    ADD CONSTRAINT ck_information_request_transition_mutation CHECK (
        mutation IN ('CREATE_DRAFT', 'ISSUE', 'RECORD_FIRST_VIEW', 'START_RESPONSE', 'SAVE_RESPONSE',
                     'ATTEST_RESPONSE', 'ADMINISTER_EVIDENCE', 'SUBMIT', 'START_REVIEW',
                     'REQUEST_CORRECTION', 'CLOSE', 'AMEND', 'REASSIGN', 'CANCEL',
                     'SUPERSEDE', 'EXPIRE', 'WITHDRAW_SUBMISSION', 'CREATE_SUCCESSOR',
                     'SCHEDULE_FOLLOW_UP', 'ASSIGN_REVIEWER', 'SAVE_REVIEW_DRAFT',
                     'RECORD_REVIEW_DECISION', 'RECORD_FINDING', 'RECORD_REVIEW_COMMENT',
                     'SETTLE_REVIEW', 'PROMOTE_FACT', 'REVOKE_FACT', 'RECORD_BUSINESS_DECISION',
                     'CHANGE_COMPLETION_GATE', 'START_CLOCK', 'PAUSE_CLOCK', 'RESUME_CLOCK',
                     'EXTEND_CLOCK', 'RECORD_REMINDER', 'RECORD_OVERDUE', 'RECORD_ESCALATION',
                     'ASSIGN_PARTY', 'REVOKE_PARTY', 'SEND_REMINDER')
        );

ALTER TABLE information_request_notice_intent
    DROP CONSTRAINT ck_information_request_notice_intent_source,
    ADD COLUMN transition_id uuid,
    ADD CONSTRAINT information_request_notice_intent_transition_fkey
        FOREIGN KEY (transition_id) REFERENCES information_request_transition (id),
    ADD CONSTRAINT ck_information_request_notice_intent_source CHECK (
        (notice_kind = 'REQUIREMENTS_AMENDED' AND amendment_id IS NOT NULL
            AND clock_event_id IS NULL AND transition_id IS NULL) OR
        (notice_kind IN ('RESPONSE_REMINDER', 'RESPONSE_OVERDUE') AND clock_event_id IS NOT NULL
            AND amendment_id IS NULL AND transition_id IS NULL) OR
        (notice_kind = 'RESPONSE_REMINDER' AND transition_id IS NOT NULL
            AND amendment_id IS NULL AND clock_event_id IS NULL)
        );

CREATE UNIQUE INDEX ux_information_request_notice_intent_transition_party
    ON information_request_notice_intent (transition_id, party_id)
    WHERE transition_id IS NOT NULL;
