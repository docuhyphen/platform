CREATE TABLE blueprint_definition (
    id                     uuid         NOT NULL,
    name                   VARCHAR(255) NOT NULL,
    summary                VARCHAR(512),
    description            VARCHAR(1024),
    scope                  VARCHAR(32)  NOT NULL
        CONSTRAINT ck_blueprint_scope CHECK (scope IN ('APP', 'ORG', 'PERSONAL')),
    organization_id        uuid         REFERENCES organization(id),
    created_by_app_user_id uuid         REFERENCES app_user(id),
    config_json            text         NOT NULL,
    is_active              boolean      NOT NULL DEFAULT true,
    is_published           boolean      NOT NULL DEFAULT false,
    is_deleted             boolean      NOT NULL DEFAULT false,
    is_template            boolean      NOT NULL DEFAULT false,
    source_template_id     uuid         REFERENCES blueprint_definition(id),
    general_tags           text         NOT NULL DEFAULT '[]',
    created_at             TIMESTAMP(6) NOT NULL,
    updated_at             TIMESTAMP(6) NOT NULL,
    CONSTRAINT blueprint_definition_pkey PRIMARY KEY (id)
);

CREATE INDEX ix_blueprint_def_org     ON blueprint_definition (organization_id)        WHERE is_deleted = false;
CREATE INDEX ix_blueprint_def_creator ON blueprint_definition (created_by_app_user_id) WHERE is_deleted = false;
CREATE INDEX ix_blueprint_def_scope   ON blueprint_definition (scope, is_active)       WHERE is_deleted = false;
