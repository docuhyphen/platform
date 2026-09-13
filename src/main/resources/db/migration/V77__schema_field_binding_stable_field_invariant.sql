-- A Schema Version must describe each stable Field at most once.
--
-- The released schema only enforces uniqueness of the immutable Field Contract inside a version, so
-- two contract versions of one Field Definition could both be bound into the same version. Every
-- consumer that addresses a field by its stable identity - blueprint defaults, value entry,
-- workflow conditions - then has two candidate answers and silently keeps whichever it reads last.
--
-- Expand-contract: the stable identity is added and backfilled, every existing conflict is recorded
-- and resolved by a deterministic rule, and only then does the invariant apply.

-- Preserves each member of a conflicted group so an operator can see exactly what a schema version
-- used to contain and which binding was kept. Written once, by this migration.
CREATE TABLE schema_field_binding_conflict (
    id                      uuid            NOT NULL,
    schema_version_id       uuid            NOT NULL REFERENCES schema_version(id),
    field_definition_id     uuid            NOT NULL REFERENCES field_definition(id),
    field_contract_id       uuid            NOT NULL REFERENCES field_contract(id),
    binding_id              uuid            NOT NULL,
    display_order           INTEGER         NOT NULL,
    section                 VARCHAR(128),
    is_required             boolean         NOT NULL,
    is_read_only            boolean         NOT NULL,
    default_value_json      text,
    visibility              VARCHAR(16)     NOT NULL,
    resolution              VARCHAR(16)     NOT NULL
        CONSTRAINT ck_binding_conflict_resolution CHECK (resolution IN ('RETAINED', 'REMOVED')),
    detected_at             TIMESTAMP(6)    NOT NULL,
    CONSTRAINT schema_field_binding_conflict_pkey PRIMARY KEY (id)
);

CREATE INDEX ix_binding_conflict_version
    ON schema_field_binding_conflict (schema_version_id, field_definition_id);

-- Expand: the stable identity of the bound field, carried on the binding itself so the invariant
-- can be a plain unique index rather than a cross-table rule.
ALTER TABLE schema_field_binding
    ADD COLUMN field_definition_id uuid;

UPDATE schema_field_binding b
SET field_definition_id = c.field_definition_id
FROM field_contract c
WHERE c.id = b.field_contract_id;

-- Report: every member of a conflicted group is recorded, kept or removed. The retained binding is
-- the one already carrying the most values, then the newest contract, then the earliest display
-- position, then the lowest id, so the outcome is deterministic and favours data already entered.
INSERT INTO schema_field_binding_conflict (id, schema_version_id, field_definition_id, field_contract_id,
                                           binding_id, display_order, section, is_required, is_read_only,
                                           default_value_json, visibility, resolution, detected_at)
SELECT gen_random_uuid(),
       ranked.schema_version_id,
       ranked.field_definition_id,
       ranked.field_contract_id,
       ranked.id,
       ranked.display_order,
       ranked.section,
       ranked.is_required,
       ranked.is_read_only,
       ranked.default_value_json,
       ranked.visibility,
       CASE WHEN ranked.rank_in_group = 1 THEN 'RETAINED' ELSE 'REMOVED' END,
       now()
FROM (
    SELECT b.*,
           COUNT(*) OVER (PARTITION BY b.schema_version_id, b.field_definition_id) AS group_size,
           ROW_NUMBER() OVER (
               PARTITION BY b.schema_version_id, b.field_definition_id
               ORDER BY (SELECT COUNT(*) FROM field_value v WHERE v.schema_field_binding_id = b.id) DESC,
                        c.contract_version DESC,
                        b.display_order ASC,
                        b.id ASC
           ) AS rank_in_group
    FROM schema_field_binding b
    JOIN field_contract c ON c.id = b.field_contract_id
) ranked
WHERE ranked.group_size > 1;

-- Resolve: an answer entered against a removed binding keeps its own row, value, and contract. Only
-- the pointer to the binding that no longer exists is cleared, so nothing entered is destroyed.
UPDATE field_value
SET schema_field_binding_id = NULL
WHERE schema_field_binding_id IN (
    SELECT binding_id FROM schema_field_binding_conflict WHERE resolution = 'REMOVED'
);

DELETE FROM schema_field_binding
WHERE id IN (
    SELECT binding_id FROM schema_field_binding_conflict WHERE resolution = 'REMOVED'
);

-- Contract: the stable identity is now always present and must be the one its contract belongs to.
-- The unique key on the contract side exists only so the pair can be referenced; a contract already
-- has one definition, so it adds no rule of its own.
ALTER TABLE schema_field_binding
    ALTER COLUMN field_definition_id SET NOT NULL;

ALTER TABLE field_contract
    ADD CONSTRAINT ux_field_contract_definition UNIQUE (id, field_definition_id);

ALTER TABLE schema_field_binding
    ADD CONSTRAINT fk_binding_contract_definition
        FOREIGN KEY (field_contract_id, field_definition_id)
        REFERENCES field_contract (id, field_definition_id);

CREATE UNIQUE INDEX ux_binding_field_definition
    ON schema_field_binding (schema_version_id, field_definition_id);
