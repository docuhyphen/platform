DROP TRIGGER information_request_requirement_append_only ON information_request_requirement;

CREATE FUNCTION information_request_requirement_binding_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'information request history is append-only';
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
        OR NEW.information_request_id IS DISTINCT FROM OLD.information_request_id
        OR NEW.source_template_requirement_id IS DISTINCT FROM OLD.source_template_requirement_id
        OR NEW.occurrence_path IS DISTINCT FROM OLD.occurrence_path
        OR NEW.created_at IS DISTINCT FROM OLD.created_at
    THEN
        RAISE EXCEPTION 'a runtime requirement keeps its identity; only its effective binding advances';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_request_template_version earlier
                 JOIN information_request_template_version later
                      ON later.template_definition_id = earlier.template_definition_id
        WHERE earlier.id = OLD.source_template_version_id
          AND later.id = NEW.source_template_version_id
          AND later.version_number > earlier.version_number
    ) THEN
        RAISE EXCEPTION 'a runtime requirement binding advances only to a later Version of its Template';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_requirement_binding_guard_write
    BEFORE UPDATE OR DELETE
    ON information_request_requirement
    FOR EACH ROW
EXECUTE FUNCTION information_request_requirement_binding_guard();

ALTER TABLE information_request_requirement
    ADD CONSTRAINT ux_information_request_requirement_stable_occurrence
        UNIQUE (information_request_id, source_template_requirement_id, occurrence_path);

ALTER TABLE information_request_requirement_revision
    DROP CONSTRAINT information_request_requirement_revision_requirement_fkey;

ALTER TABLE information_request_requirement_revision
    ADD CONSTRAINT information_request_requirement_revision_requirement_fkey
        FOREIGN KEY (information_request_requirement_id, information_request_id)
            REFERENCES information_request_requirement (id, information_request_id);

CREATE FUNCTION information_request_requirement_revision_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    effective_version     uuid;
    effective_requirement uuid;
    effective_binding     uuid;
    effective_path        VARCHAR(512);
BEGIN
    SELECT source_template_version_id, source_template_requirement_id, source_template_binding_id, occurrence_path
    INTO effective_version, effective_requirement, effective_binding, effective_path
    FROM information_request_requirement
    WHERE id = NEW.information_request_requirement_id
      AND information_request_id = NEW.information_request_id;

    IF effective_version IS DISTINCT FROM NEW.source_template_version_id
        OR effective_requirement IS DISTINCT FROM NEW.source_template_requirement_id
        OR effective_binding IS DISTINCT FROM NEW.source_template_binding_id
        OR effective_path IS DISTINCT FROM NEW.occurrence_path
    THEN
        RAISE EXCEPTION 'a requirement revision records the effective binding of its own runtime requirement';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_requirement_revision_guard_write
    BEFORE INSERT
    ON information_request_requirement_revision
    FOR EACH ROW
EXECUTE FUNCTION information_request_requirement_revision_guard();

ALTER TABLE information_request_party
    ADD CONSTRAINT uq_information_request_party_scope UNIQUE (id, information_request_id);

CREATE TABLE information_request_amendment
(
    id                        uuid        NOT NULL,
    information_request_id    uuid        NOT NULL,
    amendment_number          INTEGER     NOT NULL,
    from_template_version_id  uuid        NOT NULL,
    to_template_version_id    uuid        NOT NULL,
    reason_code               VARCHAR(128),
    amended_by_principal_kind VARCHAR(32) NOT NULL,
    amended_by_principal_id   uuid        NOT NULL,
    amended_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_amendment_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_amendment_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT information_request_amendment_from_version_fkey
        FOREIGN KEY (from_template_version_id) REFERENCES information_request_template_version (id),
    CONSTRAINT information_request_amendment_to_version_fkey
        FOREIGN KEY (to_template_version_id) REFERENCES information_request_template_version (id),
    CONSTRAINT ck_information_request_amendment_number CHECK (amendment_number >= 1),
    CONSTRAINT ck_information_request_amendment_versions CHECK (from_template_version_id <> to_template_version_id),
    CONSTRAINT ck_information_request_amendment_reason CHECK (reason_code IS NULL OR BTRIM(reason_code) <> ''),
    CONSTRAINT ck_information_request_amendment_actor CHECK (
        amended_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION', 'APPLICATION',
                                      'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        ),
    CONSTRAINT ux_information_request_amendment_number UNIQUE (information_request_id, amendment_number),
    CONSTRAINT uq_information_request_amendment_request UNIQUE (id, information_request_id)
);

CREATE FUNCTION information_request_amendment_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    prior_count INTEGER;
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_request_template_version earlier
                 JOIN information_request_template_version later
                      ON later.template_definition_id = earlier.template_definition_id
        WHERE earlier.id = NEW.from_template_version_id
          AND later.id = NEW.to_template_version_id
          AND later.version_number > earlier.version_number
          AND later.status = 'PUBLISHED'
    ) THEN
        RAISE EXCEPTION 'an amendment moves a request to a later published Version of its own Template';
    END IF;

    SELECT COUNT(*)
    INTO prior_count
    FROM information_request_amendment
    WHERE information_request_id = NEW.information_request_id;

    IF NEW.amendment_number IS DISTINCT FROM prior_count + 1 THEN
        RAISE EXCEPTION 'amendments of a request are numbered in sequence';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_amendment_write
    BEFORE INSERT
    ON information_request_amendment
    FOR EACH ROW
EXECUTE FUNCTION information_request_amendment_guard();

CREATE TABLE information_request_amendment_change
(
    id                       uuid         NOT NULL,
    amendment_id             uuid         NOT NULL,
    information_request_id   uuid         NOT NULL,
    template_requirement_id  uuid         NOT NULL,
    requirement_key          VARCHAR(128) NOT NULL,
    change_kind              VARCHAR(32)  NOT NULL,
    from_template_binding_id uuid,
    to_template_binding_id   uuid,
    reconfirmation_required  BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT information_request_amendment_change_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_amendment_change_amendment_fkey
        FOREIGN KEY (amendment_id, information_request_id)
            REFERENCES information_request_amendment (id, information_request_id),
    CONSTRAINT information_request_amendment_change_requirement_fkey
        FOREIGN KEY (template_requirement_id) REFERENCES information_request_template_requirement (id),
    CONSTRAINT information_request_amendment_change_from_binding_fkey
        FOREIGN KEY (from_template_binding_id) REFERENCES information_request_template_requirement_binding (id),
    CONSTRAINT information_request_amendment_change_to_binding_fkey
        FOREIGN KEY (to_template_binding_id) REFERENCES information_request_template_requirement_binding (id),
    CONSTRAINT ck_information_request_amendment_change_kind CHECK (
        change_kind IN ('ADDED', 'REMOVED', 'PRESENTATION_CHANGED', 'MEANING_CHANGED')
        ),
    CONSTRAINT ck_information_request_amendment_change_bindings CHECK (
        (change_kind = 'ADDED') = (from_template_binding_id IS NULL) AND
        (change_kind = 'REMOVED') = (to_template_binding_id IS NULL)
        ),
    CONSTRAINT ck_information_request_amendment_change_reconfirmation CHECK (
        NOT reconfirmation_required OR change_kind = 'MEANING_CHANGED'
        ),
    CONSTRAINT ux_information_request_amendment_change_requirement UNIQUE (amendment_id, template_requirement_id)
);

CREATE INDEX ix_information_request_amendment_change_request
    ON information_request_amendment_change (information_request_id);

CREATE TABLE information_request_notice_intent
(
    id                     uuid        NOT NULL,
    information_request_id uuid        NOT NULL,
    amendment_id           uuid        NOT NULL,
    party_id               uuid        NOT NULL,
    notice_kind            VARCHAR(64) NOT NULL,
    delivery_state         VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_notice_intent_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_notice_intent_amendment_fkey
        FOREIGN KEY (amendment_id, information_request_id)
            REFERENCES information_request_amendment (id, information_request_id),
    CONSTRAINT information_request_notice_intent_party_fkey
        FOREIGN KEY (party_id, information_request_id)
            REFERENCES information_request_party (id, information_request_id),
    CONSTRAINT ck_information_request_notice_intent_kind CHECK (notice_kind IN ('REQUIREMENTS_AMENDED')),
    CONSTRAINT ck_information_request_notice_intent_delivery CHECK (delivery_state IN ('PENDING')),
    CONSTRAINT ux_information_request_notice_intent_party UNIQUE (amendment_id, party_id)
);

CREATE INDEX ix_information_request_notice_intent_request
    ON information_request_notice_intent (information_request_id, created_at);

CREATE TRIGGER information_request_amendment_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_amendment
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER information_request_amendment_change_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_amendment_change
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER information_request_notice_intent_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_notice_intent
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

ALTER TABLE information_request_response
    ADD COLUMN reconfirmation_required_by_amendment_id uuid;

ALTER TABLE information_request_response
    ADD CONSTRAINT information_request_response_reconfirmation_fkey
        FOREIGN KEY (reconfirmation_required_by_amendment_id, information_request_id)
            REFERENCES information_request_amendment (id, information_request_id);
