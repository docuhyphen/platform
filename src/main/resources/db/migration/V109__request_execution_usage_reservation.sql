CREATE TABLE request_execution_usage_reservation
(
    id              UUID PRIMARY KEY,
    grant_id        UUID         NOT NULL REFERENCES request_execution_grant (id),
    usage_kind      VARCHAR(32)  NOT NULL,
    reservation_key VARCHAR(200) NOT NULL,
    quantity        BIGINT       NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    reserved_at     TIMESTAMP    NOT NULL,
    consumed_at     TIMESTAMP,
    released_at     TIMESTAMP,
    rolled_back_at  TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT ck_request_execution_usage_reservation_usage_kind CHECK (
        usage_kind IN ('ADDITIONAL_RECIPIENT')
        ),
    CONSTRAINT ck_request_execution_usage_reservation_status CHECK (
        status IN ('RESERVED', 'CONSUMED', 'RELEASED', 'ROLLED_BACK')
        ),
    CONSTRAINT ck_request_execution_usage_reservation_quantity CHECK (quantity > 0),
    CONSTRAINT uq_request_execution_usage_reservation_key UNIQUE (grant_id, usage_kind, reservation_key)
);

CREATE INDEX idx_request_execution_usage_reservation_grant_kind
    ON request_execution_usage_reservation (grant_id, usage_kind, status);
