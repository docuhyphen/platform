-- Repeatable and nested group definitions within one Information Request Template Version.
--
-- A requirement binding may already name an occurrence anchor key, but until now nothing defined
-- what that key referred to: any string was accepted and nothing said how many times it could
-- repeat or whether it nested inside another group. This table gives the anchor a real definition:
-- a stable key within the version, an optional parent group for nesting, and how many runtime
-- occurrences the group permits.
--
-- Groups are structure one version states, like sections, so they freeze with the version the same
-- way and are replaced whole on every rewrite of the document.

CREATE TABLE information_request_template_requirement_group
(
    id                   uuid         NOT NULL,
    template_version_id  uuid         NOT NULL,
    group_key            VARCHAR(128) NOT NULL,
    -- Null means the group repeats directly under the request. Present nests its occurrences
    -- inside one occurrence of the named parent group.
    parent_group_id      uuid,
    min_occurrences      INTEGER      NOT NULL DEFAULT 0,
    max_occurrences      INTEGER,
    CONSTRAINT information_request_template_requirement_group_pkey PRIMARY KEY (id),
    CONSTRAINT request_template_group_version_fkey
        FOREIGN KEY (template_version_id)
            REFERENCES information_request_template_version (id),
    -- Composite key so a child group can be held to a parent of its own version.
    CONSTRAINT uq_request_template_group_version UNIQUE (id, template_version_id),
    CONSTRAINT request_template_group_parent_fkey
        FOREIGN KEY (parent_group_id, template_version_id)
            REFERENCES information_request_template_requirement_group (id, template_version_id),
    CONSTRAINT ck_request_template_group_key_value CHECK (BTRIM(group_key) <> ''),
    CONSTRAINT ux_request_template_group_key UNIQUE (template_version_id, group_key),
    -- A backstop rather than the working rule: a document authoring a deeper cycle is refused by
    -- the application before any of these rows are written. This only keeps the shortest possible
    -- cycle impossible if that ever widens.
    CONSTRAINT ck_request_template_group_not_self_parent CHECK (parent_group_id IS DISTINCT FROM id),
    CONSTRAINT ck_request_template_group_min CHECK (min_occurrences >= 0),
    CONSTRAINT ck_request_template_group_max CHECK (
        max_occurrences IS NULL OR (max_occurrences >= 1 AND max_occurrences >= min_occurrences)
        )
);

CREATE INDEX ix_request_template_group_version
    ON information_request_template_requirement_group (template_version_id);
CREATE INDEX ix_request_template_group_parent
    ON information_request_template_requirement_group (parent_group_id);

-- ── Groups freeze with the version that holds them ───────────────────────────────────────────
CREATE TRIGGER request_template_group_guard_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON information_request_template_requirement_group
    FOR EACH ROW
EXECUTE FUNCTION request_template_configuration_guard();
