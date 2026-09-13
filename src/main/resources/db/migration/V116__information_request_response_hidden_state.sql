ALTER TABLE information_request_response
    ADD COLUMN active_in_response BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN hidden_by_condition_rule_key VARCHAR(128),
    ADD COLUMN hidden_data_policy VARCHAR(48),
    ADD COLUMN hidden_at TIMESTAMPTZ;

ALTER TABLE information_request_response
    ADD CONSTRAINT ck_information_request_response_hidden_data_policy CHECK (
        hidden_data_policy IS NULL
            OR hidden_data_policy IN (
                'RETAIN_SECURELY',
                'CLEAR_WITH_CONFIRMATION',
                'ARCHIVE_OUTSIDE_ACTIVE_RESPONSE'
                )
        );

ALTER TABLE information_request_response
    ADD CONSTRAINT ck_information_request_response_hidden_state CHECK (
        active_in_response = true
            OR (
                hidden_by_condition_rule_key IS NOT NULL
                    AND hidden_data_policy IS NOT NULL
                    AND hidden_at IS NOT NULL
                )
        );
