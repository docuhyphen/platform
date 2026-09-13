CREATE TABLE request_access_session
(
    id                         UUID PRIMARY KEY,
    share_link_id              UUID NOT NULL REFERENCES share_link (id),
    participant_principal_kind VARCHAR(32) NOT NULL,
    participant_principal_id   UUID NOT NULL,
    verification_strength      VARCHAR(32) NOT NULL,
    issued_at                  TIMESTAMP NOT NULL,
    expires_at                 TIMESTAMP,
    revoked_at                 TIMESTAMP,
    last_used_at               TIMESTAMP,
    use_count                  BIGINT NOT NULL DEFAULT 0,
    created_at                 TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ck_request_access_session_use_count CHECK (use_count >= 0)
);

CREATE INDEX idx_request_access_session_share_link
    ON request_access_session (share_link_id);
