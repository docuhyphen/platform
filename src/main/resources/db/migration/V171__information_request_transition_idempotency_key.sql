ALTER TABLE information_request_transition
    ADD COLUMN idempotency_key VARCHAR(256);

ALTER TABLE information_request_transition DISABLE TRIGGER information_request_transition_append_only;

UPDATE information_request_transition transition
SET idempotency_key = receipt.idempotency_key FROM command_receipt receipt
WHERE receipt.id = transition.command_receipt_id;

UPDATE information_request_transition
SET idempotency_key = 'system|' || LOWER(mutation) || '|' || information_request_id || '|' || sequence_number
WHERE idempotency_key IS NULL;

ALTER TABLE information_request_transition ENABLE TRIGGER information_request_transition_append_only;

ALTER TABLE information_request_transition
    ALTER COLUMN idempotency_key SET NOT NULL,
    ADD CONSTRAINT ck_information_request_transition_idempotency_key CHECK (BTRIM(idempotency_key) <> '');

ALTER TABLE information_request_transition
DROP
CONSTRAINT information_request_transition_command_receipt_id_fkey;

ALTER TABLE information_request_transition
    ADD CONSTRAINT information_request_transition_command_receipt_id_fkey
        FOREIGN KEY (command_receipt_id) REFERENCES command_receipt (id) DEFERRABLE INITIALLY DEFERRED;
