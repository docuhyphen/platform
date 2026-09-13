-- Reusable, versioned configuration for a request for information.
--
-- The configuration is split into what stays and what freezes. A definition is the stable identity
-- an author keeps editing and an owner keeps holding. A version is one frozen answer to that
-- identity, and once published it stops changing, because every request that pins it must resolve
-- exactly the configuration it was issued against for as long as it is kept.
--
-- Requirements carry their own stable identity at the definition, not at the version, so the same
-- requirement stays recognisable across versions and a later version can report what happened to
-- it. A binding is what places one stable requirement into one version, in one section, at one
-- position. The composite foreign keys keep the two parents of a binding in agreement: the section
-- it names must belong to its own version and the requirement it names must belong to its own
-- definition, so no version can quietly hold another owner's requirement.
--
-- Sections and bindings only carry structure here. The respondent-facing policy of a requirement,
-- its type, and the evidence rules that go with it are added on top of this structure.

-- ── Template Definition (stable identity and owner) ──────────────────────────────────────────
CREATE TABLE information_request_template_definition
(
    id                     uuid         NOT NULL,
    scope_kind             VARCHAR(32)  NOT NULL,
    scope_org_id           uuid REFERENCES organization (id),
    scope_user_id          uuid REFERENCES app_user (id),
    namespace              VARCHAR(128) NOT NULL,
    template_key           VARCHAR(128) NOT NULL,
    display_name           VARCHAR(255) NOT NULL,
    description            VARCHAR(1024),
    status                 VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    created_by_app_user_id uuid REFERENCES app_user (id),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_template_definition_pkey PRIMARY KEY (id),
    CONSTRAINT ck_request_template_definition_scope_kind CHECK (
        scope_kind IN ('PLATFORM', 'ORGANIZATION', 'PERSONAL')
        ),
    CONSTRAINT ck_request_template_definition_status CHECK (
        status IN ('DRAFT', 'PUBLISHED', 'RETIRED')
        ),
    -- A scope kind names exactly one owner. Two owners on one row, or none where one is required,
    -- leaves no answer to who governs the configuration.
    CONSTRAINT ck_request_template_definition_scope_owner CHECK (
        (scope_kind = 'PLATFORM' AND scope_org_id IS NULL AND scope_user_id IS NULL) OR
        (scope_kind = 'ORGANIZATION' AND scope_org_id IS NOT NULL AND scope_user_id IS NULL) OR
        (scope_kind = 'PERSONAL' AND scope_org_id IS NULL AND scope_user_id IS NOT NULL)
        ),
    CONSTRAINT ck_request_template_definition_key_values CHECK (
        BTRIM(namespace) <> '' AND BTRIM(template_key) <> '' AND BTRIM(display_name) <> ''
        )
);

-- A stable key belongs to one owner, so the owner is part of the key. The scope kind leads the
-- expression, so the platform sentinel and an owner whose id happens to equal it stay apart, and
-- the owner check guarantees at most one of the two owner columns contributes.
CREATE UNIQUE INDEX ux_request_template_definition_key
    ON information_request_template_definition (
                                                scope_kind,
                                                COALESCE(scope_org_id, scope_user_id,
                                                         '00000000-0000-0000-0000-000000000000'),
                                                namespace,
                                                template_key
        );

CREATE INDEX ix_request_template_definition_org
    ON information_request_template_definition (scope_org_id);
CREATE INDEX ix_request_template_definition_user
    ON information_request_template_definition (scope_user_id);

-- ── Template Version (one frozen answer to that identity) ────────────────────────────────────
CREATE TABLE information_request_template_version
(
    id                       uuid        NOT NULL,
    template_definition_id   uuid        NOT NULL,
    version_number           INTEGER     NOT NULL,
    status                   VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    -- The exact published Schema Version that typed Field requirements resolve against. It stays
    -- optional here because a version that requests only documents or assertions needs no typed
    -- data contract; requiredness is decided where requirement types are known.
    schema_version_id        uuid REFERENCES schema_version (id),
    published_at             TIMESTAMPTZ,
    published_by_app_user_id uuid REFERENCES app_user (id),
    retired_at               TIMESTAMPTZ,
    retired_by_app_user_id   uuid REFERENCES app_user (id),
    created_by_app_user_id   uuid REFERENCES app_user (id),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_template_version_pkey PRIMARY KEY (id),
    CONSTRAINT request_template_version_definition_fkey
        FOREIGN KEY (template_definition_id)
            REFERENCES information_request_template_definition (id),
    CONSTRAINT ck_request_template_version_status CHECK (
        status IN ('DRAFT', 'PUBLISHED', 'RETIRED')
        ),
    CONSTRAINT ck_request_template_version_number CHECK (version_number >= 1),
    -- A version that claims to be published or retired has to say when it was, otherwise nothing
    -- distinguishes a frozen configuration from a draft that was relabelled.
    CONSTRAINT ck_request_template_version_publication CHECK (
        (status = 'DRAFT' AND published_at IS NULL AND retired_at IS NULL) OR
        (status = 'PUBLISHED' AND published_at IS NOT NULL AND retired_at IS NULL) OR
        (status = 'RETIRED' AND published_at IS NOT NULL AND retired_at IS NOT NULL)
        ),
    CONSTRAINT ux_request_template_version_number UNIQUE (template_definition_id, version_number),
    -- Composite key so a requirement binding can be held to the definition of its own version.
    CONSTRAINT uq_request_template_version_definition UNIQUE (id, template_definition_id)
);

CREATE INDEX ix_request_template_version_definition
    ON information_request_template_version (template_definition_id, status, version_number);

-- ── Template Section (ordered grouping within one version) ───────────────────────────────────
CREATE TABLE information_request_template_section
(
    id                  uuid         NOT NULL,
    template_version_id uuid         NOT NULL,
    -- Recognises the same grouping across versions without making the position stable too.
    section_key         VARCHAR(128) NOT NULL,
    display_order       INTEGER      NOT NULL,
    title               VARCHAR(255) NOT NULL,
    help_text           VARCHAR(2048),
    CONSTRAINT information_request_template_section_pkey PRIMARY KEY (id),
    CONSTRAINT request_template_section_version_fkey
        FOREIGN KEY (template_version_id)
            REFERENCES information_request_template_version (id),
    CONSTRAINT ck_request_template_section_order CHECK (display_order >= 1),
    CONSTRAINT ck_request_template_section_values CHECK (
        BTRIM(section_key) <> '' AND BTRIM(title) <> ''
        ),
    CONSTRAINT ux_request_template_section_key UNIQUE (template_version_id, section_key),
    -- One position holds one section, so a rendered version has a single defined order.
    CONSTRAINT ux_request_template_section_order UNIQUE (template_version_id, display_order),
    -- Composite key so a requirement binding can be held to a section of its own version.
    CONSTRAINT uq_request_template_section_version UNIQUE (id, template_version_id)
);

-- ── Template Requirement (stable identity across versions) ───────────────────────────────────
CREATE TABLE information_request_template_requirement
(
    id                     uuid         NOT NULL,
    template_definition_id uuid         NOT NULL,
    requirement_key        VARCHAR(128) NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_template_requirement_pkey PRIMARY KEY (id),
    CONSTRAINT request_template_requirement_definition_fkey
        FOREIGN KEY (template_definition_id)
            REFERENCES information_request_template_definition (id),
    CONSTRAINT ck_request_template_requirement_key_value CHECK (BTRIM(requirement_key) <> ''),
    CONSTRAINT ux_request_template_requirement_key
        UNIQUE (template_definition_id, requirement_key),
    -- Composite key so a binding can be held to a requirement of its own definition.
    CONSTRAINT uq_request_template_requirement_definition UNIQUE (id, template_definition_id)
);

-- ── Requirement Binding (places one stable requirement into one version) ─────────────────────
CREATE TABLE information_request_template_requirement_binding
(
    id                      uuid    NOT NULL,
    template_version_id     uuid    NOT NULL,
    template_definition_id  uuid    NOT NULL,
    template_requirement_id uuid    NOT NULL,
    template_section_id     uuid    NOT NULL,
    display_order           INTEGER NOT NULL,
    CONSTRAINT information_request_template_requirement_binding_pkey PRIMARY KEY (id),
    CONSTRAINT request_template_binding_version_fkey
        FOREIGN KEY (template_version_id, template_definition_id)
            REFERENCES information_request_template_version (id, template_definition_id),
    CONSTRAINT request_template_binding_requirement_fkey
        FOREIGN KEY (template_requirement_id, template_definition_id)
            REFERENCES information_request_template_requirement (id, template_definition_id),
    CONSTRAINT request_template_binding_section_fkey
        FOREIGN KEY (template_section_id, template_version_id)
            REFERENCES information_request_template_section (id, template_version_id),
    CONSTRAINT ck_request_template_binding_order CHECK (display_order >= 1),
    -- One version states one thing about one requirement.
    CONSTRAINT ux_request_template_binding_requirement
        UNIQUE (template_version_id, template_requirement_id),
    CONSTRAINT ux_request_template_binding_order UNIQUE (template_section_id, display_order)
);

CREATE INDEX ix_request_template_binding_version
    ON information_request_template_requirement_binding (template_version_id, display_order);
CREATE INDEX ix_request_template_binding_requirement
    ON information_request_template_requirement_binding (template_requirement_id);

-- ── Immutability of a published version ──────────────────────────────────────────────────────
-- Publication is the point at which a configuration becomes evidence of what was asked. From then
-- on the only fact left to record about the version is that it was retired, and retirement removes
-- nothing: a retired version stays readable for the requests already pinned to it.

CREATE FUNCTION request_template_version_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'a published information request template version is immutable and cannot be removed';
        END IF;
        RETURN OLD;
    END IF;

    IF OLD.status = 'DRAFT' THEN
        RETURN NEW;
    END IF;

    IF OLD.status = 'RETIRED' THEN
        RAISE EXCEPTION 'a retired information request template version is immutable';
    END IF;

    IF NEW.status <> 'RETIRED'
        OR NEW.id <> OLD.id
        OR NEW.template_definition_id <> OLD.template_definition_id
        OR NEW.version_number <> OLD.version_number
        OR NEW.schema_version_id IS DISTINCT FROM OLD.schema_version_id
        OR NEW.published_at IS DISTINCT FROM OLD.published_at
        OR NEW.published_by_app_user_id IS DISTINCT FROM OLD.published_by_app_user_id
        OR NEW.created_at IS DISTINCT FROM OLD.created_at
        OR NEW.created_by_app_user_id IS DISTINCT FROM OLD.created_by_app_user_id
    THEN
        RAISE EXCEPTION 'a published information request template version is immutable except for retirement';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER request_template_version_guard_write
    BEFORE UPDATE OR DELETE
    ON information_request_template_version
    FOR EACH ROW
EXECUTE FUNCTION request_template_version_guard();

CREATE FUNCTION request_template_draft_version_required(candidate uuid)
    RETURNS void AS
$$
DECLARE
    candidate_status VARCHAR(16);
BEGIN
    SELECT status
    INTO candidate_status
    FROM information_request_template_version
    WHERE id = candidate;

    IF candidate_status IS DISTINCT FROM 'DRAFT' THEN
        RAISE EXCEPTION 'the configuration of a published information request template version is immutable';
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Guards both ends of a move, so configuration can neither be added to a frozen version nor taken
-- out of one.
CREATE FUNCTION request_template_configuration_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP <> 'INSERT' THEN
        PERFORM request_template_draft_version_required(OLD.template_version_id);
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    PERFORM request_template_draft_version_required(NEW.template_version_id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER request_template_section_guard_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON information_request_template_section
    FOR EACH ROW
EXECUTE FUNCTION request_template_configuration_guard();

CREATE TRIGGER request_template_binding_guard_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON information_request_template_requirement_binding
    FOR EACH ROW
EXECUTE FUNCTION request_template_configuration_guard();

-- ── Stability of a requirement identity ──────────────────────────────────────────────────────
-- The point of a stable requirement is that a response recorded against it years ago still means
-- the same thing. Rewriting its key or moving it to another definition would silently reinterpret
-- every version and every response that named it.

CREATE FUNCTION request_template_requirement_identity_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NEW.id <> OLD.id
        OR NEW.template_definition_id <> OLD.template_definition_id
        OR NEW.requirement_key <> OLD.requirement_key
    THEN
        RAISE EXCEPTION 'the stable identity of an information request template requirement cannot be rewritten';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER request_template_requirement_identity_guard_update
    BEFORE UPDATE
    ON information_request_template_requirement
    FOR EACH ROW
EXECUTE FUNCTION request_template_requirement_identity_guard();
