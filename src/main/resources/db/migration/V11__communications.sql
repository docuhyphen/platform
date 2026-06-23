SET search_path TO public;

CREATE TABLE communication (
    id                      UUID         NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    summary                 VARCHAR(512),
    description             VARCHAR(1024),
    scope                   VARCHAR(32)  NOT NULL,
    organization_id         UUID         REFERENCES organization(id),
    created_by_app_user_id  UUID         REFERENCES app_user(id),
    subject                 TEXT         NOT NULL,
    body                    TEXT         NOT NULL,
    channel_overrides_json  TEXT,
    general_tags            TEXT         NOT NULL DEFAULT '[]',
    is_active               BOOLEAN      NOT NULL DEFAULT true,
    is_published            BOOLEAN      NOT NULL DEFAULT false,
    is_deleted              BOOLEAN      NOT NULL DEFAULT false,
    is_template             BOOLEAN      NOT NULL DEFAULT false,
    source_template_id      UUID         REFERENCES communication(id),
    created_at              TIMESTAMP(6) NOT NULL,
    updated_at              TIMESTAMP(6) NOT NULL,

    CONSTRAINT communication_pkey PRIMARY KEY (id),
    CONSTRAINT ck_communication_scope CHECK (scope IN ('PLATFORM', 'ORG', 'PERSONAL')),
    CONSTRAINT ck_communication_org_scope CHECK (
        (scope = 'PLATFORM' AND organization_id IS NULL)
        OR (scope = 'ORG'      AND organization_id IS NOT NULL)
        OR (scope = 'PERSONAL' AND organization_id IS NULL)
    )
);

CREATE INDEX ix_comm_org     ON communication (organization_id)        WHERE is_deleted = false;
CREATE INDEX ix_comm_creator ON communication (created_by_app_user_id) WHERE is_deleted = false;
CREATE INDEX ix_comm_scope   ON communication (scope, is_active)       WHERE is_deleted = false;
