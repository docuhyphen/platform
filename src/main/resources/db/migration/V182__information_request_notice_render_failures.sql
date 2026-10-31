CREATE TABLE information_request_notice_render_failure
(
    id                     uuid         NOT NULL,
    notice_intent_id       uuid         NOT NULL,
    information_request_id uuid         NOT NULL,
    attempt_number         INTEGER      NOT NULL,
    failure_code           VARCHAR(128) NOT NULL,
    failed_at              TIMESTAMPTZ  NOT NULL,
    CONSTRAINT information_request_notice_render_failure_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_notice_render_failure_intent_fkey
        FOREIGN KEY (notice_intent_id) REFERENCES information_request_notice_intent (id) ON DELETE CASCADE,
    CONSTRAINT information_request_notice_render_failure_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id) ON DELETE CASCADE,
    CONSTRAINT ux_information_request_notice_render_failure_attempt UNIQUE (notice_intent_id, attempt_number),
    CONSTRAINT ck_information_request_notice_render_failure_attempt CHECK (attempt_number >= 1),
    CONSTRAINT ck_information_request_notice_render_failure_code CHECK (BTRIM(failure_code) <> '')
);

CREATE INDEX ix_information_request_notice_render_failure_request
    ON information_request_notice_render_failure (information_request_id);

CREATE TRIGGER information_request_notice_render_failure_unchanged
    BEFORE UPDATE
    ON information_request_notice_render_failure
    FOR EACH ROW
    EXECUTE FUNCTION information_request_append_only_guard();
