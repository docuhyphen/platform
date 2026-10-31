CREATE TABLE information_request_response_archive
(
    id                                 uuid         NOT NULL,
    information_request_id             uuid         NOT NULL,
    information_request_requirement_id uuid         NOT NULL,
    response_id                        uuid         NOT NULL,
    occurrence_path                    VARCHAR(512) NOT NULL,
    condition_rule_key                 VARCHAR(128) NOT NULL,
    response_revision                  BIGINT       NOT NULL,
    disposition                        VARCHAR(32)  NOT NULL,
    narrative                          text,
    field_contract_id                  uuid,
    field_value_type                   VARCHAR(32),
    field_value_json                   text,
    field_value_set_etag               VARCHAR(128),
    archived_by_principal_kind         VARCHAR(32)  NOT NULL,
    archived_by_principal_id           uuid         NOT NULL,
    archived_by_session_ref            VARCHAR(64),
    archived_at                        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_response_archive_pkey PRIMARY KEY (id),
    CONSTRAINT request_response_archive_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id) ON DELETE CASCADE,
    CONSTRAINT request_response_archive_requirement_fkey
        FOREIGN KEY (information_request_requirement_id) REFERENCES information_request_requirement (id)
            ON DELETE CASCADE,
    CONSTRAINT request_response_archive_response_fkey
        FOREIGN KEY (response_id) REFERENCES information_request_response (id) ON DELETE CASCADE,
    CONSTRAINT request_response_archive_field_contract_fkey
        FOREIGN KEY (field_contract_id) REFERENCES field_contract (id),
    CONSTRAINT ck_request_response_archive_path CHECK (BTRIM(occurrence_path) <> '' AND occurrence_path <> '$'),
    CONSTRAINT ck_request_response_archive_rule CHECK (BTRIM(condition_rule_key) <> ''),
    CONSTRAINT ck_request_response_archive_revision CHECK (response_revision >= 1),
    CONSTRAINT ck_request_response_archive_disposition CHECK (
        disposition IN ('NOT_ANSWERED', 'PROVIDED', 'PARTIALLY_PROVIDED', 'NOT_APPLICABLE',
                        'UNAVAILABLE', 'EXCEPTION_REQUESTED', 'SATISFIED_BY_REFERENCE', 'WAIVED')
        ),
    CONSTRAINT ck_request_response_archive_field CHECK (
        (field_contract_id IS NULL AND field_value_type IS NULL AND field_value_json IS NULL)
            OR (field_contract_id IS NOT NULL AND field_value_type IS NOT NULL AND field_value_json IS NOT NULL)
        ),
    CONSTRAINT ck_request_response_archive_field_type CHECK (
        field_value_type IS NULL OR field_value_type IN (
                                                         'SHORT_TEXT', 'LONG_TEXT', 'BOOLEAN', 'INTEGER', 'DECIMAL',
                                                         'DATE', 'DATE_TIME',
                                                         'SINGLE_SELECT', 'MULTI_SELECT'
            )
        ),
    CONSTRAINT ck_request_response_archive_content CHECK (
        disposition <> 'NOT_ANSWERED' OR narrative IS NOT NULL OR field_value_json IS NOT NULL
        ),
    CONSTRAINT ck_request_response_archive_actor CHECK (
        archived_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                       'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE INDEX ix_request_response_archive_requirement
    ON information_request_response_archive (information_request_id, information_request_requirement_id, archived_at);

CREATE TRIGGER information_request_response_archive_append_only
    BEFORE UPDATE OR
DELETE
ON information_request_response_archive
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TABLE information_request_response_condition_effect
(
    id                                 uuid         NOT NULL,
    information_request_id             uuid         NOT NULL,
    information_request_requirement_id uuid         NOT NULL,
    response_id                        uuid         NOT NULL,
    transition_id                      uuid         NOT NULL,
    occurrence_path                    VARCHAR(512) NOT NULL,
    rule_key                           VARCHAR(128) NOT NULL,
    expression_version                 INTEGER      NOT NULL,
    evaluation_state                   VARCHAR(16)  NOT NULL,
    hidden_data_policy                 VARCHAR(48)  NOT NULL,
    effect                             VARCHAR(32)  NOT NULL,
    archive_id                         uuid,
    response_revision                  BIGINT       NOT NULL,
    recorded_by_principal_kind         VARCHAR(32)  NOT NULL,
    recorded_by_principal_id           uuid         NOT NULL,
    recorded_by_session_ref            VARCHAR(64),
    occurred_at                        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_response_condition_effect_pkey PRIMARY KEY (id),
    CONSTRAINT request_response_condition_effect_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id) ON DELETE CASCADE,
    CONSTRAINT request_response_condition_effect_requirement_fkey
        FOREIGN KEY (information_request_requirement_id) REFERENCES information_request_requirement (id)
            ON DELETE CASCADE,
    CONSTRAINT request_response_condition_effect_response_fkey
        FOREIGN KEY (response_id) REFERENCES information_request_response (id) ON DELETE CASCADE,
    CONSTRAINT request_response_condition_effect_transition_fkey
        FOREIGN KEY (transition_id) REFERENCES information_request_transition (id) ON DELETE CASCADE,
    CONSTRAINT request_response_condition_effect_archive_fkey
        FOREIGN KEY (archive_id) REFERENCES information_request_response_archive (id) ON DELETE CASCADE,
    CONSTRAINT ck_request_response_condition_effect_path CHECK (
        BTRIM(occurrence_path) <> '' AND occurrence_path <> '$'
        ),
    CONSTRAINT ck_request_response_condition_effect_rule CHECK (BTRIM(rule_key) <> ''),
    CONSTRAINT ck_request_response_condition_effect_expression CHECK (expression_version >= 1),
    CONSTRAINT ck_request_response_condition_effect_state CHECK (
        evaluation_state IN ('TRUE', 'FALSE', 'UNKNOWN')
        ),
    CONSTRAINT ck_request_response_condition_effect_policy CHECK (
        hidden_data_policy IN ('RETAIN_SECURELY', 'CLEAR_WITH_CONFIRMATION', 'ARCHIVE_OUTSIDE_ACTIVE_RESPONSE')
        ),
    CONSTRAINT ck_request_response_condition_effect_kind CHECK (
        effect IN ('HIDDEN', 'CLEARED', 'ARCHIVED', 'REACTIVATED')
        ),
    CONSTRAINT ck_request_response_condition_effect_reactivation CHECK (
        (effect = 'REACTIVATED') = (evaluation_state = 'TRUE')
        ),
    CONSTRAINT ck_request_response_condition_effect_cleared CHECK (
        effect <> 'CLEARED' OR hidden_data_policy = 'CLEAR_WITH_CONFIRMATION'
        ),
    CONSTRAINT ck_request_response_condition_effect_archived CHECK (
        (effect = 'ARCHIVED') = (archive_id IS NOT NULL)
            AND (effect <> 'ARCHIVED' OR hidden_data_policy = 'ARCHIVE_OUTSIDE_ACTIVE_RESPONSE')
        ),
    CONSTRAINT ck_request_response_condition_effect_revision CHECK (response_revision >= 1),
    CONSTRAINT ck_request_response_condition_effect_actor CHECK (
        recorded_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                       'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE INDEX ix_request_response_condition_effect_request
    ON information_request_response_condition_effect (information_request_id, occurred_at);

CREATE INDEX ix_request_response_condition_effect_transition
    ON information_request_response_condition_effect (transition_id);

CREATE TRIGGER information_request_response_condition_effect_append_only
    BEFORE UPDATE OR
DELETE
ON information_request_response_condition_effect
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();
