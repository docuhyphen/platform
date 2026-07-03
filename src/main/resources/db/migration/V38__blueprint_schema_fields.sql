-- Blueprint <-> Schema integration.
-- A blueprint may optionally reference a business schema (by stable schema_definition_id) and carry
-- default field values, so every Exchange started from it is pre-seeded with that schema + values.
-- See BLUEPRINT-SCHEMA-INTEGRATION-PLAN.md. Defaults are keyed by the stable field_definition_id
-- (not the version-specific field_contract_id) so they survive schema re-publishing.

ALTER TABLE blueprint_definition
    ADD COLUMN schema_definition_id uuid NULL REFERENCES schema_definition(id) ON DELETE SET NULL;

CREATE TABLE blueprint_field_default (
    id                          uuid            NOT NULL,
    blueprint_definition_id     uuid            NOT NULL
        REFERENCES blueprint_definition(id) ON DELETE CASCADE,
    field_definition_id         uuid            NOT NULL REFERENCES field_definition(id),
    value_type                  VARCHAR(32)     NOT NULL,
    value_json                  TEXT            NULL,
    display_order               INTEGER         NOT NULL DEFAULT 0,
    CONSTRAINT blueprint_field_default_pkey PRIMARY KEY (id)
);

CREATE INDEX ix_blueprint_field_default_blueprint
    ON blueprint_field_default (blueprint_definition_id);
