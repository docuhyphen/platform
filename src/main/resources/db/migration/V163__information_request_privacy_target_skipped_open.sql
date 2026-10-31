ALTER TABLE information_request_privacy_target
DROP
CONSTRAINT ck_information_request_privacy_target_outcome;

ALTER TABLE information_request_privacy_target
    ADD CONSTRAINT ck_information_request_privacy_target_outcome CHECK (
        outcome IN ('EXPORTED', 'CORRECTED', 'RESTRICTED', 'DISPOSAL_CLAIMED', 'SKIPPED_OPEN', 'REFUSED')
        );
