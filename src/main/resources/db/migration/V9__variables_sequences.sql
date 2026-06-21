-- Sequence definitions per org (auto-incrementing named counters)
CREATE TABLE sequence_definition (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         UUID        NOT NULL REFERENCES organization(id),
    name                    VARCHAR(255) NOT NULL,
    key                     VARCHAR(64)  NOT NULL,
    current_value           BIGINT      NOT NULL DEFAULT 0,
    pad_width               INT         NOT NULL DEFAULT 0,
    prefix                  VARCHAR(64),
    suffix                  VARCHAR(64),
    reset_period            VARCHAR(16) NOT NULL DEFAULT 'NEVER',
    last_reset_at           TIMESTAMPTZ,
    is_active               BOOLEAN     NOT NULL DEFAULT TRUE,
    is_deleted              BOOLEAN     NOT NULL DEFAULT FALSE,
    created_by_app_user_id  UUID        REFERENCES app_user(id),
    created_at              TIMESTAMP(6) NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sequence_key UNIQUE (organization_id, key)
);

-- User-defined variable definitions (ORG or PERSONAL scope)
CREATE TABLE variable_definition (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    key                     VARCHAR(64)  NOT NULL,
    default_value           TEXT,
    scope                   VARCHAR(16)  NOT NULL CHECK (scope IN ('ORG', 'PERSONAL')),
    organization_id         UUID        REFERENCES organization(id),
    created_by_app_user_id  UUID        NOT NULL REFERENCES app_user(id),
    is_active               BOOLEAN     NOT NULL DEFAULT TRUE,
    is_deleted              BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP(6) NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_variable_org   ON variable_definition (organization_id, key) WHERE scope = 'ORG' AND is_deleted = FALSE;
CREATE UNIQUE INDEX uq_variable_user  ON variable_definition (created_by_app_user_id, key) WHERE scope = 'PERSONAL' AND is_deleted = FALSE;
CREATE INDEX ix_sequence_org          ON sequence_definition (organization_id) WHERE is_deleted = FALSE;
CREATE INDEX ix_variable_scope        ON variable_definition (scope, organization_id) WHERE is_deleted = FALSE;
