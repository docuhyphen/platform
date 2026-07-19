ALTER TABLE exchange_recipient
    DROP CONSTRAINT ck_exchange_recipient_participant_acceptance;

ALTER TABLE exchange_recipient
    ADD CONSTRAINT ck_exchange_recipient_participant_acceptance
        CHECK (
            purpose = 'PRIMARY'
            OR acceptance_status = 'NOT_REQUIRED'
            OR (
                purpose = 'PARTICIPANT'
                AND selection_type IN ('TRUSTED_PERSON', 'TRUSTED_GROUP')
                AND acceptance_status IN ('PENDING', 'ACCEPTED', 'REJECTED')
            )
        );

CREATE INDEX idx_exchange_recipient_pending_participant
    ON exchange_recipient (exchange_id, id)
    WHERE purpose = 'PARTICIPANT' AND acceptance_status = 'PENDING';
