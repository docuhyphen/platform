-- Versioned condition rules and their predicates within one Information Request Template Version.
--
-- A requirement binding could already name a conditional rule key, but until now nothing defined
-- what that key referred to: the authoring API validated a rule document in memory and then
-- discarded it, so a rule existed only for the length of one write request. This gives the rule a
-- real, versioned, auditable definition: a stable key within the version, an expression version so
-- a later runtime can tell which reading of the rule a stored evaluation used, and an ordered list
-- of predicates that each read exactly one source -- a Field's current value or another
-- requirement's current disposition -- and compare it against a literal.
--
-- Rules are structure one version states, like sections and groups, so they freeze with the version
-- the same way and are replaced whole on every rewrite of the document.

CREATE TABLE information_request_template_condition_rule
(
    id                  uuid         NOT NULL,
    template_version_id uuid         NOT NULL,
    rule_key            VARCHAR(128) NOT NULL,
    expression_version  INTEGER      NOT NULL DEFAULT 1,
    CONSTRAINT information_request_template_condition_rule_pkey PRIMARY KEY (id),
    CONSTRAINT request_template_condition_rule_version_fkey
        FOREIGN KEY (template_version_id)
            REFERENCES information_request_template_version (id),
    -- Composite key so a predicate can be held to a rule of its own version.
    CONSTRAINT uq_request_template_condition_rule_version UNIQUE (id, template_version_id),
    CONSTRAINT ck_request_template_condition_rule_key_value CHECK (BTRIM(rule_key) <> ''),
    CONSTRAINT ux_request_template_condition_rule_key UNIQUE (template_version_id, rule_key),
    CONSTRAINT ck_request_template_condition_rule_expression_version CHECK (expression_version >= 1)
);

CREATE INDEX ix_request_template_condition_rule_version
    ON information_request_template_condition_rule (template_version_id);

-- ── The predicates one rule states ───────────────────────────────────────────────────────────
-- A predicate reads exactly one source: a Field's current value, named by field_definition_id, or
-- another requirement's current disposition, named by source_requirement_key. Which one is present
-- decides which of the two the evaluator reads; naming both, or neither, is not a rule any
-- evaluator could resolve.
CREATE TABLE information_request_template_condition_predicate
(
    id                     uuid         NOT NULL,
    condition_rule_id      uuid         NOT NULL,
    template_version_id    uuid         NOT NULL,
    display_order          INTEGER      NOT NULL,
    source_requirement_key VARCHAR(128),
    field_definition_id    uuid,
    value_type             VARCHAR(32),
    operator               VARCHAR(32)  NOT NULL,
    expected_disposition   VARCHAR(32),
    text_value             text,
    number_value           NUMERIC(38, 10),
    bool_value             boolean,
    date_value             date,
    datetime_value         timestamp,
    datetime_offset_minutes INTEGER,
    CONSTRAINT information_request_template_condition_predicate_pkey PRIMARY KEY (id),
    CONSTRAINT request_template_condition_predicate_rule_fkey
        FOREIGN KEY (condition_rule_id, template_version_id)
            REFERENCES information_request_template_condition_rule (id, template_version_id),
    -- Composite key so a list literal can be held to a predicate of its own version.
    CONSTRAINT uq_request_template_condition_predicate_version UNIQUE (id, template_version_id),
    CONSTRAINT ck_request_template_condition_predicate_source CHECK (
        (source_requirement_key IS NOT NULL) <> (field_definition_id IS NOT NULL)
        ),
    CONSTRAINT ck_request_template_condition_predicate_key_value CHECK (
        source_requirement_key IS NULL OR BTRIM(source_requirement_key) <> ''
        ),
    CONSTRAINT ck_request_template_condition_predicate_value_type CHECK (
        value_type IS NULL OR value_type IN (
            'SHORT_TEXT', 'LONG_TEXT', 'BOOLEAN', 'INTEGER', 'DECIMAL', 'DATE', 'DATE_TIME',
            'SINGLE_SELECT', 'MULTI_SELECT'
            )
        ),
    CONSTRAINT ck_request_template_condition_predicate_operator CHECK (
        operator IN (
            'EQUALS', 'NOT_EQUALS', 'LESS_THAN', 'LESS_THAN_OR_EQUAL', 'GREATER_THAN',
            'GREATER_THAN_OR_EQUAL', 'CONTAINS', 'STARTS_WITH', 'IN', 'NOT_IN', 'IS_EMPTY',
            'IS_NOT_EMPTY'
            )
        ),
    CONSTRAINT ck_request_template_condition_predicate_disposition CHECK (
        expected_disposition IS NULL OR expected_disposition IN (
            'NOT_ANSWERED', 'PROVIDED', 'PARTIALLY_PROVIDED', 'NOT_APPLICABLE', 'UNAVAILABLE',
            'EXCEPTION_REQUESTED', 'SATISFIED_BY_REFERENCE', 'WAIVED'
            )
        )
);

CREATE INDEX ix_request_template_condition_predicate_rule
    ON information_request_template_condition_predicate (condition_rule_id);
CREATE INDEX ix_request_template_condition_predicate_version
    ON information_request_template_condition_predicate (template_version_id);

-- ── The list literal a membership predicate compares against ────────────────────────────────
-- Only an IN / NOT_IN predicate against a SINGLE_SELECT or MULTI_SELECT Field carries more than one
-- literal, so this holds the option codes the same way FieldValueSelection holds a Field answer's
-- own selected codes: one row per code, ordered the way it was authored.
CREATE TABLE information_request_template_condition_predicate_literal
(
    id                      uuid         NOT NULL,
    condition_predicate_id  uuid         NOT NULL,
    template_version_id     uuid         NOT NULL,
    literal_value           VARCHAR(256) NOT NULL,
    display_order           INTEGER      NOT NULL,
    CONSTRAINT information_request_template_condition_predicate_literal_pkey PRIMARY KEY (id),
    CONSTRAINT request_template_condition_predicate_literal_fkey
        FOREIGN KEY (condition_predicate_id, template_version_id)
            REFERENCES information_request_template_condition_predicate (id, template_version_id),
    CONSTRAINT ck_request_template_condition_predicate_literal_value CHECK (BTRIM(literal_value) <> '')
);

CREATE INDEX ix_request_template_condition_predicate_literal_predicate
    ON information_request_template_condition_predicate_literal (condition_predicate_id);
CREATE INDEX ix_request_template_condition_predicate_literal_version
    ON information_request_template_condition_predicate_literal (template_version_id);

-- ── Rules, predicates, and their literals freeze with the version that holds them ───────────
CREATE TRIGGER request_template_condition_rule_guard_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON information_request_template_condition_rule
    FOR EACH ROW
EXECUTE FUNCTION request_template_configuration_guard();

CREATE TRIGGER request_template_condition_predicate_guard_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON information_request_template_condition_predicate
    FOR EACH ROW
EXECUTE FUNCTION request_template_configuration_guard();

CREATE TRIGGER request_template_condition_predicate_literal_guard_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON information_request_template_condition_predicate_literal
    FOR EACH ROW
EXECUTE FUNCTION request_template_configuration_guard();
