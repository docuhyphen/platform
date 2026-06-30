ALTER TABLE exchange
    DROP CONSTRAINT exchange_status_check,
    ADD CONSTRAINT exchange_status_check
        CHECK (status IN ('INITIATED', 'ACCEPTED_STARTED', 'ENDED', 'REJECTED', 'RESCINDED'));
