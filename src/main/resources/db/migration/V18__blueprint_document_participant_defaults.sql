-- Extract the exchangeDocuments[] and participants[] arrays out of
-- blueprint_definition.config_json into normalized child tables.
--
-- These arrays embedded foreign-key references as opaque strings inside JSON
-- (libraryDocumentId -> document_library, principalId -> a principal) with no
-- referential integrity and no way to query "which blueprints reference X". They are
-- now relational rows. The remaining scalar defaults (name, message, allow* flags,
-- recipientConfiguration) legitimately stay in config_json as a client-side prefill
-- snapshot.

CREATE TABLE blueprint_document_default (
    id                      uuid         NOT NULL,
    blueprint_definition_id uuid         NOT NULL REFERENCES blueprint_definition(id) ON DELETE CASCADE,
    title                   VARCHAR(255) NOT NULL,
    restricted_type         VARCHAR(16),
    restrict_type           boolean      NOT NULL DEFAULT false,
    required                boolean      NOT NULL DEFAULT false,
    library_document_id     uuid         REFERENCES document_library(id) ON DELETE SET NULL,
    display_order           INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT blueprint_document_default_pkey PRIMARY KEY (id)
);

CREATE INDEX ix_bp_doc_default_blueprint ON blueprint_document_default (blueprint_definition_id);
CREATE INDEX ix_bp_doc_default_libdoc    ON blueprint_document_default (library_document_id);

CREATE TABLE blueprint_participant_default (
    id                      uuid         NOT NULL,
    blueprint_definition_id uuid         NOT NULL REFERENCES blueprint_definition(id) ON DELETE CASCADE,
    principal_kind          VARCHAR(32)  NOT NULL,
    principal_id            VARCHAR(64)  NOT NULL,
    role_name               VARCHAR(64)  NOT NULL,
    display_order           INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT blueprint_participant_default_role_check CHECK (
        role_name IN ('OWNER', 'EDITOR', 'REVIEWER', 'SIGNER', 'VIEWER', 'COMMENTER', 'PARTICIPANT')
    ),
    CONSTRAINT blueprint_participant_default_pkey PRIMARY KEY (id)
);

CREATE INDEX ix_bp_part_default_blueprint ON blueprint_participant_default (blueprint_definition_id);
CREATE INDEX ix_bp_part_default_principal ON blueprint_participant_default (principal_kind, principal_id);

-- Backfill child rows from any existing config_json (all stored config_json is valid
-- JSON, enforced at write time). COALESCE guards rows that omit the arrays.
INSERT INTO blueprint_document_default
    (id, blueprint_definition_id, title, restricted_type, restrict_type, required, library_document_id, display_order)
SELECT gen_random_uuid(),
       b.id,
       elem->>'title',
       elem->>'restrictedType',
       COALESCE((elem->>'restrictType')::boolean, false),
       COALESCE((elem->>'required')::boolean, false),
       NULLIF(elem->>'libraryDocumentId', '')::uuid,
       (ord - 1)::int
FROM blueprint_definition b
CROSS JOIN LATERAL jsonb_array_elements(
    COALESCE(NULLIF(b.config_json, '')::jsonb -> 'exchangeDocuments', '[]'::jsonb)
) WITH ORDINALITY AS t(elem, ord)
WHERE elem->>'title' IS NOT NULL;

INSERT INTO blueprint_participant_default
    (id, blueprint_definition_id, principal_kind, principal_id, role_name, display_order)
SELECT gen_random_uuid(),
       b.id,
       elem->>'principalKind',
       elem->>'principalId',
       elem->>'roleName',
       (ord - 1)::int
FROM blueprint_definition b
CROSS JOIN LATERAL jsonb_array_elements(
    COALESCE(NULLIF(b.config_json, '')::jsonb -> 'participants', '[]'::jsonb)
) WITH ORDINALITY AS t(elem, ord)
WHERE elem->>'principalId' IS NOT NULL
  AND elem->>'principalKind' IS NOT NULL
  AND elem->>'roleName' IS NOT NULL;

-- Strip the two extracted arrays from config_json so the data lives only in the tables.
UPDATE blueprint_definition
SET config_json = ((config_json)::jsonb - 'exchangeDocuments' - 'participants')::text
WHERE config_json IS NOT NULL
  AND config_json <> '';
