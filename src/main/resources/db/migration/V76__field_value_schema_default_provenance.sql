-- Field values may now originate from a schema binding's configured default value, which is
-- materialised when a schema is assigned to a resource. That origin is neither a user's own answer
-- nor a blueprint default, so it needs its own provenance value.
--
-- Forward-only: the existing values keep their recorded provenance and no row is rewritten.

ALTER TABLE field_value
    DROP CONSTRAINT ck_field_value_provenance;

ALTER TABLE field_value
    ADD CONSTRAINT ck_field_value_provenance CHECK (provenance IN
        ('USER', 'SCHEMA_DEFAULT', 'BLUEPRINT_DEFAULT', 'API', 'WORKFLOW_ACTION', 'CALCULATED', 'MIGRATION'));
