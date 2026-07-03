-- Configurable Fields and Business Schema Engine - foundation tables.
-- See FIELDS-FEATURE.md. Lean first release: PLATFORM + ORGANIZATION scopes, EXCHANGE resource,
-- immutable published versions, typed values with provenance.
--
-- scope_kind is a deliberately extensible string (not an FK to an enum table) so future
-- BUSINESS_UNIT / TEAM kinds can be added without rewriting every table. scope_org_id is the
-- owner id when scope_kind = 'ORGANIZATION' and must be NULL for 'PLATFORM'.

-- ── Field Definition (stable identity of a reusable business attribute) ──────────────────────
CREATE TABLE field_definition (
    id                      uuid            NOT NULL,
    scope_kind              VARCHAR(32)     NOT NULL
        CONSTRAINT ck_field_def_scope_kind CHECK (scope_kind IN ('PLATFORM', 'ORGANIZATION')),
    scope_org_id            uuid            REFERENCES organization(id),
    namespace               VARCHAR(128)    NOT NULL,
    field_key               VARCHAR(128)    NOT NULL,
    status                  VARCHAR(16)     NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT ck_field_def_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    created_by_app_user_id  uuid            REFERENCES app_user(id),
    created_at              TIMESTAMP(6)    NOT NULL,
    updated_at              TIMESTAMP(6)    NOT NULL,
    CONSTRAINT field_definition_pkey PRIMARY KEY (id),
    -- PLATFORM scope must not carry an org id; ORGANIZATION scope must.
    CONSTRAINT ck_field_def_scope_org CHECK (
        (scope_kind = 'PLATFORM' AND scope_org_id IS NULL) OR
        (scope_kind = 'ORGANIZATION' AND scope_org_id IS NOT NULL)
    )
);

-- Stable key uniqueness within owner scope and namespace (not globally by display name).
CREATE UNIQUE INDEX ux_field_def_key
    ON field_definition (scope_kind, COALESCE(scope_org_id, '00000000-0000-0000-0000-000000000000'), namespace, field_key);
CREATE INDEX ix_field_def_org ON field_definition (scope_org_id);

-- ── Field Contract (immutable version of a Field Definition) ─────────────────────────────────
CREATE TABLE field_contract (
    id                      uuid            NOT NULL,
    field_definition_id     uuid            NOT NULL REFERENCES field_definition(id),
    contract_version        INTEGER         NOT NULL,
    value_type              VARCHAR(32)     NOT NULL
        CONSTRAINT ck_field_contract_type CHECK (value_type IN
            ('SHORT_TEXT','LONG_TEXT','BOOLEAN','INTEGER','DECIMAL','DATE','DATE_TIME',
             'SINGLE_SELECT','MULTI_SELECT')),
    type_contract_version   INTEGER         NOT NULL DEFAULT 1,
    label                   VARCHAR(255)    NOT NULL,
    description             VARCHAR(1024),
    help_text               VARCHAR(1024),
    -- JSON object of type-specific constraints (min, max, length, precision, pattern, etc.).
    constraints_json        text            NOT NULL DEFAULT '{}',
    -- JSON array of inline options for SINGLE_SELECT / MULTI_SELECT contracts.
    options_json            text            NOT NULL DEFAULT '[]',
    data_classification     VARCHAR(16)     NOT NULL DEFAULT 'INTERNAL'
        CONSTRAINT ck_field_contract_classification CHECK (data_classification IN
            ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED')),
    is_searchable           boolean         NOT NULL DEFAULT false,
    is_filterable           boolean         NOT NULL DEFAULT false,
    is_sortable             boolean         NOT NULL DEFAULT false,
    is_reportable           boolean         NOT NULL DEFAULT false,
    -- JSON array of external system aliases (reserved in first release).
    external_aliases_json   text            NOT NULL DEFAULT '[]',
    created_at              TIMESTAMP(6)    NOT NULL,
    CONSTRAINT field_contract_pkey PRIMARY KEY (id),
    CONSTRAINT ux_field_contract_version UNIQUE (field_definition_id, contract_version)
);

CREATE INDEX ix_field_contract_def ON field_contract (field_definition_id);

-- ── Schema Definition (stable identity of a configurable business concept) ───────────────────
CREATE TABLE schema_definition (
    id                      uuid            NOT NULL,
    scope_kind              VARCHAR(32)     NOT NULL
        CONSTRAINT ck_schema_def_scope_kind CHECK (scope_kind IN ('PLATFORM', 'ORGANIZATION')),
    scope_org_id            uuid            REFERENCES organization(id),
    namespace               VARCHAR(128)    NOT NULL,
    schema_key              VARCHAR(128)    NOT NULL,
    display_name            VARCHAR(255)    NOT NULL,
    description             VARCHAR(1024),
    target_resource_type    VARCHAR(48)     NOT NULL DEFAULT 'EXCHANGE',
    status                  VARCHAR(16)     NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT ck_schema_def_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    created_by_app_user_id  uuid            REFERENCES app_user(id),
    created_at              TIMESTAMP(6)    NOT NULL,
    updated_at              TIMESTAMP(6)    NOT NULL,
    CONSTRAINT schema_definition_pkey PRIMARY KEY (id),
    CONSTRAINT ck_schema_def_scope_org CHECK (
        (scope_kind = 'PLATFORM' AND scope_org_id IS NULL) OR
        (scope_kind = 'ORGANIZATION' AND scope_org_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX ux_schema_def_key
    ON schema_definition (scope_kind, COALESCE(scope_org_id, '00000000-0000-0000-0000-000000000000'), namespace, schema_key);
CREATE INDEX ix_schema_def_org ON schema_definition (scope_org_id);

-- ── Schema Version (immutable published contract) ────────────────────────────────────────────
CREATE TABLE schema_version (
    id                      uuid            NOT NULL,
    schema_definition_id    uuid            NOT NULL REFERENCES schema_definition(id),
    version_number          INTEGER         NOT NULL,
    status                  VARCHAR(16)     NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT ck_schema_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    compatibility           VARCHAR(16)
        CONSTRAINT ck_schema_version_compat CHECK (compatibility IS NULL OR compatibility IN
            ('ADDITIVE','COMPATIBLE','BREAKING')),
    -- JSON array of schema-level validation rules (reserved; empty in first release).
    schema_rules_json       text            NOT NULL DEFAULT '[]',
    published_at            TIMESTAMP(6),
    published_by_app_user_id uuid           REFERENCES app_user(id),
    created_at              TIMESTAMP(6)    NOT NULL,
    CONSTRAINT schema_version_pkey PRIMARY KEY (id),
    CONSTRAINT ux_schema_version_number UNIQUE (schema_definition_id, version_number)
);

CREATE INDEX ix_schema_version_def ON schema_version (schema_definition_id, status);

-- ── Schema Field Binding (places a Field Contract into a Schema Version) ─────────────────────
CREATE TABLE schema_field_binding (
    id                      uuid            NOT NULL,
    schema_version_id       uuid            NOT NULL REFERENCES schema_version(id),
    field_contract_id       uuid            NOT NULL REFERENCES field_contract(id),
    display_order           INTEGER         NOT NULL DEFAULT 0,
    section                 VARCHAR(128),
    is_required             boolean         NOT NULL DEFAULT false,
    is_read_only            boolean         NOT NULL DEFAULT false,
    -- Canonical JSON of the static default value (reserved simple defaults in first release).
    default_value_json      text,
    visibility              VARCHAR(16)     NOT NULL DEFAULT 'INTERNAL'
        CONSTRAINT ck_binding_visibility CHECK (visibility IN
            ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED')),
    CONSTRAINT schema_field_binding_pkey PRIMARY KEY (id),
    CONSTRAINT ux_binding_contract UNIQUE (schema_version_id, field_contract_id)
);

CREATE INDEX ix_binding_version ON schema_field_binding (schema_version_id, display_order);

-- ── Schema Assignment (pins one resource to one published Schema Version) ────────────────────
CREATE TABLE schema_assignment (
    id                      uuid            NOT NULL,
    resource_type           VARCHAR(48)     NOT NULL,
    resource_id             uuid            NOT NULL,
    schema_version_id       uuid            NOT NULL REFERENCES schema_version(id),
    scope_kind              VARCHAR(32)     NOT NULL
        CONSTRAINT ck_assignment_scope_kind CHECK (scope_kind IN ('PLATFORM', 'ORGANIZATION')),
    scope_org_id            uuid            REFERENCES organization(id),
    assignment_source       VARCHAR(24)     NOT NULL DEFAULT 'MANUAL'
        CONSTRAINT ck_assignment_source CHECK (assignment_source IN
            ('MANUAL','BLUEPRINT','API','MIGRATION')),
    assigned_by_app_user_id uuid            REFERENCES app_user(id),
    assigned_at             TIMESTAMP(6)    NOT NULL,
    CONSTRAINT schema_assignment_pkey PRIMARY KEY (id),
    -- One primary business schema per resource in the first release.
    CONSTRAINT ux_assignment_resource UNIQUE (resource_type, resource_id)
);

CREATE INDEX ix_assignment_version ON schema_assignment (schema_version_id);

-- ── Field Value (typed value against an assignment / binding) ────────────────────────────────
CREATE TABLE field_value (
    id                      uuid            NOT NULL,
    schema_assignment_id    uuid            NOT NULL REFERENCES schema_assignment(id),
    schema_field_binding_id uuid            REFERENCES schema_field_binding(id),
    field_contract_id       uuid            NOT NULL REFERENCES field_contract(id),
    resource_type           VARCHAR(48)     NOT NULL,
    resource_id             uuid            NOT NULL,
    value_type              VARCHAR(32)     NOT NULL,
    -- Typed scalar columns; the relevant one is populated based on value_type.
    -- SINGLE_SELECT / MULTI_SELECT canonical option codes live in field_value_selection.
    text_value              text,
    number_value            NUMERIC(38, 10),
    bool_value              boolean,
    date_value              DATE,
    datetime_value          TIMESTAMP(6),
    provenance              VARCHAR(24)     NOT NULL DEFAULT 'USER'
        CONSTRAINT ck_field_value_provenance CHECK (provenance IN
            ('USER','BLUEPRINT_DEFAULT','API','WORKFLOW_ACTION','CALCULATED','MIGRATION')),
    created_at              TIMESTAMP(6)    NOT NULL,
    updated_at              TIMESTAMP(6)    NOT NULL,
    updated_by_app_user_id  uuid            REFERENCES app_user(id),
    CONSTRAINT field_value_pkey PRIMARY KEY (id),
    -- One value row per binding per assignment.
    CONSTRAINT ux_field_value_binding UNIQUE (schema_assignment_id, field_contract_id)
);

CREATE INDEX ix_field_value_resource ON field_value (resource_type, resource_id);
CREATE INDEX ix_field_value_assignment ON field_value (schema_assignment_id);

-- ── Field Value Selection (canonical option codes for select-typed values) ──────────────────
CREATE TABLE field_value_selection (
    id                      uuid            NOT NULL,
    field_value_id          uuid            NOT NULL REFERENCES field_value(id) ON DELETE CASCADE,
    option_code             VARCHAR(128)    NOT NULL,
    display_order           INTEGER         NOT NULL DEFAULT 0,
    CONSTRAINT field_value_selection_pkey PRIMARY KEY (id),
    CONSTRAINT ux_value_selection UNIQUE (field_value_id, option_code)
);

CREATE INDEX ix_value_selection_value ON field_value_selection (field_value_id);
