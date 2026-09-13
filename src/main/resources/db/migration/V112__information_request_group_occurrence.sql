-- Runtime repetitions of a Template group, scoped to one Information Request.
--
-- The template only states a group's cardinality and nesting; issuance materializes that into
-- actual occurrences the request carries so a Field, Document, or Response Attestation Requirement
-- instance can anchor to a stable path rather than to a hardcoded "root".

CREATE TABLE information_request_group_occurrence
(
    id                      uuid         NOT NULL,
    information_request_id uuid         NOT NULL,
    source_template_group_id uuid       NOT NULL,
    -- Null means this repetition sits directly under the request, mirroring a group with no
    -- parent group of its own.
    parent_occurrence_id    uuid,
    occurrence_index        INTEGER      NOT NULL,
    occurrence_path         VARCHAR(512) NOT NULL,
    created_at              TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT information_request_group_occurrence_pkey PRIMARY KEY (id),
    CONSTRAINT request_group_occurrence_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT request_group_occurrence_group_fkey
        FOREIGN KEY (source_template_group_id)
            REFERENCES information_request_template_requirement_group (id),
    -- Composite key so a nested occurrence can be held to a parent of its own request.
    CONSTRAINT uq_request_group_occurrence_request UNIQUE (id, information_request_id),
    CONSTRAINT request_group_occurrence_parent_fkey
        FOREIGN KEY (parent_occurrence_id, information_request_id)
            REFERENCES information_request_group_occurrence (id, information_request_id),
    CONSTRAINT ck_request_group_occurrence_not_self_parent CHECK (parent_occurrence_id IS DISTINCT FROM id),
    CONSTRAINT ck_request_group_occurrence_index CHECK (occurrence_index >= 0),
    CONSTRAINT ck_request_group_occurrence_path_value CHECK (BTRIM(occurrence_path) <> ''),
    CONSTRAINT ux_request_group_occurrence_path UNIQUE (information_request_id, occurrence_path)
);

CREATE INDEX ix_request_group_occurrence_request
    ON information_request_group_occurrence (information_request_id);
CREATE INDEX ix_request_group_occurrence_group
    ON information_request_group_occurrence (source_template_group_id);
CREATE INDEX ix_request_group_occurrence_parent
    ON information_request_group_occurrence (parent_occurrence_id);
