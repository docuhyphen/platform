-- Who left an answer, and who assigned a Schema, named as the canonical principal.
--
-- The released schema records both as a foreign key into app_user, so only a registered user can be
-- named. An external participant, a link-verified recipient, a registered application, and a service
-- principal all reach the same Fields engine, and an answer attributed to any of them today either
-- names the wrong table or fails that foreign key outright. The canonical (kind, id) pair names every
-- one of them without introducing a second identity model.
--
-- A stored answer also has no history: the row is overwritten in place, so the exact value behind a
-- later record cannot be resolved. An append-only revision beside the live answer keeps every
-- recorded change addressable, and each revision keeps the chosen options that belonged to it.
--
-- Expand-contract: the canonical columns are added and filled from the trustworthy legacy key, the
-- legacy key stays and keeps its meaning for registered users, and a consistency rule ties the two
-- together so neither can drift from the other while both exist.

-- ── Canonical authorship of a stored answer ─────────────────────────────────────────────────
ALTER TABLE field_value
    ADD COLUMN updated_by_principal_kind VARCHAR(32),
    ADD COLUMN updated_by_principal_id   uuid,
    -- Non-secret reference to the access session the change was made in. It identifies the session
    -- record, never the credential that opened it.
    ADD COLUMN updated_by_session_ref    VARCHAR(64);

-- Every author recorded so far is a registered user, which is the only thing the legacy key can mean.
-- An answer whose author was never recorded stays unrecorded rather than gaining an invented one.
UPDATE field_value
SET updated_by_principal_kind = 'USER',
    updated_by_principal_id   = updated_by_app_user_id
WHERE updated_by_app_user_id IS NOT NULL;

ALTER TABLE field_value
    ADD CONSTRAINT ck_field_value_principal_kind CHECK (
        updated_by_principal_kind IS NULL
        OR updated_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                         'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
    ),
    -- Half a principal identifies nobody, so a kind and an ID only ever appear together.
    ADD CONSTRAINT ck_field_value_principal_pair CHECK (
        (updated_by_principal_kind IS NULL) = (updated_by_principal_id IS NULL)
    ),
    -- While the legacy key exists it may only ever name the same registered user the canonical pair
    -- names, so dropping it later cannot change what any row says.
    ADD CONSTRAINT ck_field_value_principal_legacy CHECK (
        updated_by_app_user_id IS NULL
        OR (updated_by_principal_kind = 'USER' AND updated_by_principal_id = updated_by_app_user_id)
    );

CREATE INDEX ix_field_value_updated_by_principal
    ON field_value (updated_by_principal_kind, updated_by_principal_id);

-- ── Canonical authorship of a Schema Assignment ─────────────────────────────────────────────
ALTER TABLE schema_assignment
    ADD COLUMN assigned_by_principal_kind VARCHAR(32),
    ADD COLUMN assigned_by_principal_id   uuid,
    ADD COLUMN assigned_by_session_ref    VARCHAR(64);

UPDATE schema_assignment
SET assigned_by_principal_kind = 'USER',
    assigned_by_principal_id   = assigned_by_app_user_id
WHERE assigned_by_app_user_id IS NOT NULL;

ALTER TABLE schema_assignment
    ADD CONSTRAINT ck_assignment_principal_kind CHECK (
        assigned_by_principal_kind IS NULL
        OR assigned_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                          'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
    ),
    ADD CONSTRAINT ck_assignment_principal_pair CHECK (
        (assigned_by_principal_kind IS NULL) = (assigned_by_principal_id IS NULL)
    ),
    ADD CONSTRAINT ck_assignment_principal_legacy CHECK (
        assigned_by_app_user_id IS NULL
        OR (assigned_by_principal_kind = 'USER' AND assigned_by_principal_id = assigned_by_app_user_id)
    );

-- ── Recorded revisions of an answer ─────────────────────────────────────────────────────────
-- A revision outlives the answer it records: removing a Schema Assignment removes the live answer,
-- its set, and the assignment itself, while the history of what was answered must survive. The
-- answer, its set, and its assignment are therefore kept as identities rather than as foreign keys.
-- The field contract is a foreign key because a published contract version is never deleted.
CREATE TABLE field_value_revision (
    id                          uuid            NOT NULL,
    field_value_id              uuid            NOT NULL,
    field_value_set_id          uuid            NOT NULL,
    schema_assignment_id        uuid            NOT NULL,
    schema_field_binding_id     uuid,
    field_contract_id           uuid            NOT NULL REFERENCES field_contract(id),
    -- Monotonic within the set and question, so a repetition numbers its own answers.
    revision_number             INTEGER         NOT NULL,
    value_type                  VARCHAR(32)     NOT NULL
        CONSTRAINT ck_field_value_revision_type CHECK (value_type IN
            ('SHORT_TEXT', 'LONG_TEXT', 'BOOLEAN', 'INTEGER', 'DECIMAL', 'DATE', 'DATE_TIME',
             'SINGLE_SELECT', 'MULTI_SELECT')),
    text_value                  text,
    number_value                NUMERIC(38, 10),
    bool_value                  boolean,
    date_value                  DATE,
    datetime_value              TIMESTAMP(6) WITH TIME ZONE,
    datetime_offset_minutes     INTEGER,
    -- Whether this revision emptied the answer. A cleared answer is recorded rather than erased.
    is_cleared                  boolean         NOT NULL DEFAULT false,
    provenance                  VARCHAR(24)     NOT NULL
        CONSTRAINT ck_field_value_revision_provenance CHECK (provenance IN
            ('USER', 'SCHEMA_DEFAULT', 'BLUEPRINT_DEFAULT', 'API', 'WORKFLOW_ACTION', 'CALCULATED',
             'MIGRATION')),
    recorded_by_principal_kind  VARCHAR(32),
    recorded_by_principal_id    uuid,
    recorded_by_session_ref     VARCHAR(64),
    recorded_by_app_user_id     uuid            REFERENCES app_user(id),
    recorded_at                 TIMESTAMP(6)    NOT NULL,
    CONSTRAINT field_value_revision_pkey PRIMARY KEY (id),
    CONSTRAINT ux_field_value_revision_number UNIQUE (field_value_set_id, field_contract_id, revision_number),
    CONSTRAINT ck_field_value_revision_number CHECK (revision_number >= 1),
    CONSTRAINT ck_field_value_revision_principal_kind CHECK (
        recorded_by_principal_kind IS NULL
        OR recorded_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                          'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
    ),
    CONSTRAINT ck_field_value_revision_principal_pair CHECK (
        (recorded_by_principal_kind IS NULL) = (recorded_by_principal_id IS NULL)
    ),
    CONSTRAINT ck_field_value_revision_principal_legacy CHECK (
        recorded_by_app_user_id IS NULL
        OR (recorded_by_principal_kind = 'USER' AND recorded_by_principal_id = recorded_by_app_user_id)
    ),
    CONSTRAINT ck_field_value_revision_datetime_offset CHECK (
        datetime_offset_minutes IS NULL
        OR (datetime_value IS NOT NULL AND datetime_offset_minutes BETWEEN -1080 AND 1080)
    )
);

CREATE INDEX ix_field_value_revision_value ON field_value_revision (field_value_id);
CREATE INDEX ix_field_value_revision_set ON field_value_revision (field_value_set_id, field_contract_id);
CREATE INDEX ix_field_value_revision_assignment ON field_value_revision (schema_assignment_id);

-- Chosen options belong to the revision that recorded them, mirroring how the live answer stores them.
CREATE TABLE field_value_revision_selection (
    id                          uuid            NOT NULL,
    field_value_revision_id     uuid            NOT NULL
        REFERENCES field_value_revision(id) ON DELETE CASCADE,
    option_code                 VARCHAR(128)    NOT NULL,
    display_order               INTEGER         NOT NULL DEFAULT 0,
    CONSTRAINT field_value_revision_selection_pkey PRIMARY KEY (id),
    CONSTRAINT ux_field_value_revision_selection UNIQUE (field_value_revision_id, option_code)
);

CREATE INDEX ix_field_value_revision_selection_revision
    ON field_value_revision_selection (field_value_revision_id);

-- A recorded revision is never rewritten. Disposal may still remove one, which is what keeps
-- retention and record-disposal able to act on it later, but its content cannot be altered in place.
CREATE OR REPLACE FUNCTION field_value_revision_is_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'field_value_revision rows are append-only and cannot be modified';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_field_value_revision_immutable
    BEFORE UPDATE ON field_value_revision
    FOR EACH ROW EXECUTE FUNCTION field_value_revision_is_immutable();

CREATE TRIGGER trg_field_value_revision_selection_immutable
    BEFORE UPDATE ON field_value_revision_selection
    FOR EACH ROW EXECUTE FUNCTION field_value_revision_is_immutable();

-- Expand: every answer already stored asserts its own last recorded change through its provenance,
-- its updated_at, and its author, so that state becomes the answer's first addressable revision.
-- Nothing is invented here: an answer whose author was never recorded gets a revision that names no
-- principal, exactly as the answer does.
INSERT INTO field_value_revision (id, field_value_id, field_value_set_id, schema_assignment_id,
                                  schema_field_binding_id, field_contract_id, revision_number,
                                  value_type, text_value, number_value, bool_value, date_value,
                                  datetime_value, datetime_offset_minutes, is_cleared, provenance,
                                  recorded_by_principal_kind, recorded_by_principal_id,
                                  recorded_by_app_user_id, recorded_at)
SELECT gen_random_uuid(), v.id, v.field_value_set_id, v.schema_assignment_id,
       v.schema_field_binding_id, v.field_contract_id, 1,
       v.value_type, v.text_value, v.number_value, v.bool_value, v.date_value,
       v.datetime_value, v.datetime_offset_minutes, false, v.provenance,
       v.updated_by_principal_kind, v.updated_by_principal_id,
       v.updated_by_app_user_id, v.updated_at
FROM field_value v;

INSERT INTO field_value_revision_selection (id, field_value_revision_id, option_code, display_order)
SELECT gen_random_uuid(), r.id, s.option_code, s.display_order
FROM field_value_revision r
JOIN field_value_selection s ON s.field_value_id = r.field_value_id;
