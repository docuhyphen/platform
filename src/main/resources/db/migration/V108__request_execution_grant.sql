CREATE TABLE request_execution_grant
(
    id                             UUID PRIMARY KEY,
    request_id                     UUID        NOT NULL UNIQUE REFERENCES information_request (id),
    owner_type                     VARCHAR(32) NOT NULL,
    owner_organization_id          UUID REFERENCES organization (id),
    owner_user_id                  UUID REFERENCES app_user (id),
    plan_code                      VARCHAR(32) NOT NULL,
    subscription_status            VARCHAR(32) NOT NULL,
    enforcement_mode               VARCHAR(16) NOT NULL,
    trial_expires_at               TIMESTAMP,
    mutation_allowance_expires_at  TIMESTAMP,
    additional_recipient_cap       BIGINT,
    revoked_at                     TIMESTAMP,
    revoked_reason                 VARCHAR(1024),
    issued_at                      TIMESTAMP   NOT NULL,
    created_at                     TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT ck_request_execution_grant_owner_type CHECK (
        owner_type IN ('ORGANIZATION', 'USER')
        ),
    CONSTRAINT ck_request_execution_grant_owner_reference CHECK (
        (owner_type = 'ORGANIZATION' AND owner_organization_id IS NOT NULL AND owner_user_id IS NULL) OR
        (owner_type = 'USER' AND owner_organization_id IS NULL AND owner_user_id IS NOT NULL)
        )
);

CREATE INDEX idx_request_execution_grant_owner
    ON request_execution_grant (owner_type, COALESCE(owner_organization_id, owner_user_id));
