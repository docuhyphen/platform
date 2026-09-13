ALTER TABLE information_request_template_condition_rule
    ADD COLUMN hidden_data_policy VARCHAR(48) NOT NULL DEFAULT 'RETAIN_SECURELY';

ALTER TABLE information_request_template_condition_rule
    ADD CONSTRAINT ck_request_template_condition_rule_hidden_data_policy CHECK (
        hidden_data_policy IN (
            'RETAIN_SECURELY',
            'CLEAR_WITH_CONFIRMATION',
            'ARCHIVE_OUTSIDE_ACTIVE_RESPONSE'
            )
        );
