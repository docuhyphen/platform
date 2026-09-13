-- A typed answer belongs to a set of answers, not directly to the assignment.
--
-- The released schema keys an answer by its assignment and field contract, so a resource can hold
-- exactly one answer per question for as long as it exists. A process that asks the same group of
-- questions once per item has no way to express the second item, and every later record that must
-- point at the exact answers behind a submission has nothing stable to point at.
--
-- Expand-contract: each assignment gains the one set that holds the answers it gives as itself,
-- every stored answer is carried into that set, and only then does uniqueness move from the
-- assignment to the set. A repetition of a group is a second set under the same assignment, named
-- by its own path, which is why uniqueness has to move for the model to mean anything.

-- The root set holds the answers an assignment gives as itself. An occurrence set holds one
-- repetition of a repeatable group and is named by its path within the assignment.
CREATE TABLE field_value_set (
    id                      uuid            NOT NULL,
    schema_assignment_id    uuid            NOT NULL REFERENCES schema_assignment(id),
    set_kind                VARCHAR(16)     NOT NULL
        CONSTRAINT ck_field_value_set_kind CHECK (set_kind IN ('ROOT', 'OCCURRENCE')),
    occurrence_path         VARCHAR(512),
    created_at              TIMESTAMP(6)    NOT NULL,
    updated_at              TIMESTAMP(6)    NOT NULL,
    CONSTRAINT field_value_set_pkey PRIMARY KEY (id),
    -- Only a repetition has somewhere to be; the root set is the assignment itself.
    CONSTRAINT ck_field_value_set_occurrence CHECK (
        (set_kind = 'ROOT' AND occurrence_path IS NULL)
        OR (set_kind = 'OCCURRENCE' AND occurrence_path IS NOT NULL)
    ),
    -- Referenced by the answer side so a set and the answer using it cannot name two assignments.
    CONSTRAINT ux_field_value_set_assignment UNIQUE (id, schema_assignment_id)
);

-- An assignment answers once as itself, so a second root set would make its answers ambiguous.
CREATE UNIQUE INDEX ux_field_value_set_root
    ON field_value_set (schema_assignment_id)
    WHERE set_kind = 'ROOT';

-- A repetition is addressed by its path, so two sets cannot claim the same one.
CREATE UNIQUE INDEX ux_field_value_set_occurrence_path
    ON field_value_set (schema_assignment_id, occurrence_path)
    WHERE occurrence_path IS NOT NULL;

CREATE INDEX ix_field_value_set_assignment ON field_value_set (schema_assignment_id);

-- Expand: every assignment that exists today answers as itself, so each gains exactly one root set,
-- dated from the moment its schema was assigned rather than from the moment of this upgrade.
INSERT INTO field_value_set (id, schema_assignment_id, set_kind, occurrence_path, created_at, updated_at)
SELECT gen_random_uuid(), a.id, 'ROOT', NULL, a.assigned_at, a.assigned_at
FROM schema_assignment a;

ALTER TABLE field_value
    ADD COLUMN field_value_set_id uuid;

-- Every answer stored so far was given by its assignment as itself, so it belongs to the root set.
UPDATE field_value v
SET field_value_set_id = s.id
FROM field_value_set s
WHERE s.schema_assignment_id = v.schema_assignment_id
  AND s.set_kind = 'ROOT';

-- Contract: an answer now always names the set it belongs to, and that set must belong to the same
-- assignment as the answer.
ALTER TABLE field_value
    ALTER COLUMN field_value_set_id SET NOT NULL;

ALTER TABLE field_value
    ADD CONSTRAINT fk_field_value_set
        FOREIGN KEY (field_value_set_id, schema_assignment_id)
        REFERENCES field_value_set (id, schema_assignment_id);

-- One answer per question per set replaces one answer per question per assignment. With one root
-- set per assignment these say the same thing about every row that exists today, and only the new
-- rule also allows a repeated group to answer the same question once per repetition.
ALTER TABLE field_value
    DROP CONSTRAINT ux_field_value_binding;

ALTER TABLE field_value
    ADD CONSTRAINT ux_field_value_set_binding UNIQUE (field_value_set_id, field_contract_id);
