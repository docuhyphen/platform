ALTER TABLE information_request_notice_intent
    DROP CONSTRAINT ck_information_request_notice_intent_delivery,
    DROP CONSTRAINT ck_information_request_notice_intent_kind,
    DROP COLUMN delivery_state,
    ALTER COLUMN amendment_id DROP NOT NULL,
    ADD COLUMN clock_event_id          uuid,
    ADD COLUMN source_communication_id uuid,
    ADD CONSTRAINT information_request_notice_intent_clock_event_fkey
        FOREIGN KEY (clock_event_id) REFERENCES information_request_clock_event (id),
    ADD CONSTRAINT information_request_notice_intent_communication_fkey
        FOREIGN KEY (source_communication_id) REFERENCES communication (id),
    ADD CONSTRAINT ck_information_request_notice_intent_kind CHECK (
        notice_kind IN ('REQUIREMENTS_AMENDED', 'RESPONSE_REMINDER', 'RESPONSE_OVERDUE')
        ),
    ADD CONSTRAINT ck_information_request_notice_intent_source CHECK (
        (notice_kind = 'REQUIREMENTS_AMENDED' AND amendment_id IS NOT NULL AND clock_event_id IS NULL) OR
        (notice_kind IN ('RESPONSE_REMINDER', 'RESPONSE_OVERDUE') AND clock_event_id IS NOT NULL AND amendment_id IS NULL)
        );

CREATE UNIQUE INDEX ux_information_request_notice_intent_clock_party
    ON information_request_notice_intent (clock_event_id, party_id)
    WHERE clock_event_id IS NOT NULL;

ALTER TABLE information_request_clock_policy_version
    ADD COLUMN reminder_communication_id uuid,
    ADD COLUMN overdue_communication_id  uuid,
    ADD CONSTRAINT fk_clock_policy_version_reminder_communication
        FOREIGN KEY (reminder_communication_id) REFERENCES communication (id),
    ADD CONSTRAINT fk_clock_policy_version_overdue_communication
        FOREIGN KEY (overdue_communication_id) REFERENCES communication (id);

CREATE TABLE information_request_notice_claim
(
    notice_intent_id       uuid         NOT NULL,
    information_request_id uuid         NOT NULL,
    claimed_by             VARCHAR(128) NOT NULL,
    claimed_at             TIMESTAMPTZ  NOT NULL,
    CONSTRAINT information_request_notice_claim_pkey PRIMARY KEY (notice_intent_id),
    CONSTRAINT information_request_notice_claim_intent_fkey
        FOREIGN KEY (notice_intent_id) REFERENCES information_request_notice_intent (id),
    CONSTRAINT information_request_notice_claim_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT ck_information_request_notice_claim_worker CHECK (BTRIM(claimed_by) <> '')
);

CREATE TABLE information_request_outbound_notice
(
    id                       uuid         NOT NULL,
    notice_intent_id         uuid         NOT NULL,
    information_request_id   uuid         NOT NULL,
    party_id                 uuid         NOT NULL,
    recipient_principal_kind VARCHAR(32),
    recipient_principal_id   uuid,
    channel                  VARCHAR(16)  NOT NULL,
    recipient_endpoint       VARCHAR(320),
    endpoint_state           VARCHAR(16)  NOT NULL,
    rendered_subject         TEXT         NOT NULL,
    rendered_body            TEXT         NOT NULL,
    content_hash_algorithm   VARCHAR(16)  NOT NULL,
    rendered_content_hash    VARCHAR(64)  NOT NULL,
    source_kind              VARCHAR(32)  NOT NULL,
    source_communication_id  uuid,
    source_content_hash      VARCHAR(64)  NOT NULL,
    idempotency_key          VARCHAR(256) NOT NULL,
    rendered_at              TIMESTAMPTZ  NOT NULL,
    CONSTRAINT information_request_outbound_notice_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_outbound_notice_claim_fkey
        FOREIGN KEY (notice_intent_id) REFERENCES information_request_notice_claim (notice_intent_id),
    CONSTRAINT information_request_outbound_notice_party_fkey
        FOREIGN KEY (party_id, information_request_id) REFERENCES information_request_party (id, information_request_id),
    CONSTRAINT information_request_outbound_notice_communication_fkey
        FOREIGN KEY (source_communication_id) REFERENCES communication (id),
    CONSTRAINT ux_information_request_outbound_notice_intent UNIQUE (notice_intent_id),
    CONSTRAINT ux_information_request_outbound_notice_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_information_request_outbound_notice_channel CHECK (channel IN ('EMAIL')),
    CONSTRAINT ck_information_request_outbound_notice_endpoint CHECK (
        (endpoint_state = 'RESOLVED' AND recipient_endpoint IS NOT NULL AND BTRIM(recipient_endpoint) <> '') OR
        (endpoint_state = 'MISSING' AND recipient_endpoint IS NULL)
        ),
    CONSTRAINT ck_information_request_outbound_notice_source CHECK (
        (source_kind = 'COMMUNICATION' AND source_communication_id IS NOT NULL) OR
        (source_kind = 'PLATFORM_DEFAULT' AND source_communication_id IS NULL)
        ),
    CONSTRAINT ck_information_request_outbound_notice_hash CHECK (
        content_hash_algorithm = 'SHA_256' AND
        rendered_content_hash ~ '^[0-9a-f]{64}$' AND
        source_content_hash ~ '^[0-9a-f]{64}$'
        ),
    CONSTRAINT ck_information_request_outbound_notice_recipient CHECK (
        (recipient_principal_kind IS NULL) = (recipient_principal_id IS NULL)
        )
);

CREATE INDEX ix_information_request_outbound_notice_request
    ON information_request_outbound_notice (information_request_id, rendered_at);

CREATE TABLE information_request_notice_sequence_allocation
(
    id                 uuid        NOT NULL,
    outbound_notice_id uuid        NOT NULL,
    sequence_key       VARCHAR(64) NOT NULL,
    allocated_value    BIGINT      NOT NULL,
    rendered_value     TEXT        NOT NULL,
    CONSTRAINT information_request_notice_sequence_allocation_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_notice_sequence_allocation_notice_fkey
        FOREIGN KEY (outbound_notice_id) REFERENCES information_request_outbound_notice (id),
    CONSTRAINT ux_information_request_notice_sequence_key UNIQUE (outbound_notice_id, sequence_key)
);

CREATE TABLE information_request_notice_delivery_attempt
(
    id                     uuid         NOT NULL,
    outbound_notice_id     uuid         NOT NULL,
    information_request_id uuid         NOT NULL,
    attempt_number         INTEGER      NOT NULL,
    channel                VARCHAR(16)  NOT NULL,
    outcome                VARCHAR(16)  NOT NULL,
    failure_code           VARCHAR(128),
    attempted_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT information_request_notice_delivery_attempt_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_notice_delivery_attempt_notice_fkey
        FOREIGN KEY (outbound_notice_id) REFERENCES information_request_outbound_notice (id),
    CONSTRAINT ux_information_request_notice_attempt_number UNIQUE (outbound_notice_id, attempt_number),
    CONSTRAINT ck_information_request_notice_attempt_number CHECK (attempt_number >= 1),
    CONSTRAINT ck_information_request_notice_attempt_channel CHECK (channel IN ('EMAIL')),
    CONSTRAINT ck_information_request_notice_attempt_outcome CHECK (outcome IN ('DELIVERED', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_information_request_notice_attempt_failure CHECK ((outcome = 'DELIVERED') = (failure_code IS NULL))
);

CREATE INDEX ix_information_request_notice_attempt_request
    ON information_request_notice_delivery_attempt (information_request_id, attempted_at);

CREATE TRIGGER information_request_notice_claim_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_notice_claim
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER information_request_outbound_notice_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_outbound_notice
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER information_request_notice_sequence_allocation_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_notice_sequence_allocation
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER information_request_notice_delivery_attempt_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_notice_delivery_attempt
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();
