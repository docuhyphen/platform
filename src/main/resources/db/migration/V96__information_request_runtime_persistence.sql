-- Runtime persistence for Information Requests.
--
-- A request pins one published Template Version under the same owner as its parent Exchange. The
-- aggregate and party rows can move forward through command services, so they carry optimistic
-- revisions. Requirement revisions and transitions are history: they append, and the current
-- pointer moves to the newest revision without rewriting the old one.

CREATE TABLE information_request
(
    id                       uuid        NOT NULL,
    exchange_id              uuid        NOT NULL,
    template_version_id       uuid        NOT NULL,
    owner_type               VARCHAR(32) NOT NULL,
    owner_organization_id    uuid REFERENCES organization (id),
    owner_user_id            uuid REFERENCES app_user (id),
    state                    VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    gates_exchange_closure   BOOLEAN     NOT NULL DEFAULT TRUE,
    aggregate_revision       BIGINT      NOT NULL DEFAULT 1,
    party_revision           BIGINT      NOT NULL DEFAULT 1,
    created_by_app_user_id   uuid REFERENCES app_user (id),
    issued_at                TIMESTAMPTZ,
    closed_at                TIMESTAMPTZ,
    cancelled_at             TIMESTAMPTZ,
    superseded_at            TIMESTAMPTZ,
    superseded_by_request_id uuid,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_exchange_fkey
        FOREIGN KEY (exchange_id) REFERENCES exchange (id),
    CONSTRAINT information_request_template_version_fkey
        FOREIGN KEY (template_version_id) REFERENCES information_request_template_version (id),
    CONSTRAINT information_request_superseded_by_fkey
        FOREIGN KEY (superseded_by_request_id) REFERENCES information_request (id),
    CONSTRAINT ck_information_request_owner_type CHECK (
        owner_type IN ('ORGANIZATION', 'USER')
        ),
    CONSTRAINT ck_information_request_owner CHECK (
        (owner_type = 'ORGANIZATION' AND owner_organization_id IS NOT NULL AND owner_user_id IS NULL) OR
        (owner_type = 'USER' AND owner_organization_id IS NULL AND owner_user_id IS NOT NULL)
        ),
    CONSTRAINT ck_information_request_state CHECK (
        state IN ('DRAFT', 'ISSUED', 'IN_PROGRESS', 'SUBMITTED', 'UNDER_REVIEW',
                  'CHANGES_REQUESTED', 'CLOSED', 'CANCELLED', 'SUPERSEDED', 'EXPIRED')
        ),
    CONSTRAINT ck_information_request_revisions CHECK (
        aggregate_revision >= 1 AND party_revision >= 1
        ),
    CONSTRAINT ck_information_request_terminal_dates CHECK (
        (state <> 'ISSUED' OR issued_at IS NOT NULL) AND
        (state <> 'IN_PROGRESS' OR issued_at IS NOT NULL) AND
        (state <> 'SUBMITTED' OR issued_at IS NOT NULL) AND
        (state <> 'UNDER_REVIEW' OR issued_at IS NOT NULL) AND
        (state <> 'CHANGES_REQUESTED' OR issued_at IS NOT NULL) AND
        (state <> 'CLOSED' OR closed_at IS NOT NULL) AND
        (state <> 'CANCELLED' OR cancelled_at IS NOT NULL) AND
        (state <> 'SUPERSEDED' OR superseded_at IS NOT NULL)
        )
);

CREATE INDEX ix_information_request_exchange
    ON information_request (exchange_id, state, created_at);

CREATE INDEX ix_information_request_template_version
    ON information_request (template_version_id);

CREATE INDEX ix_information_request_owner
    ON information_request (
                            owner_type,
                            COALESCE(owner_organization_id, owner_user_id)
        );

CREATE FUNCTION information_request_owner_and_template_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    exchange_owner_org uuid;
    exchange_owner_user uuid;
    template_scope_kind VARCHAR(32);
    template_scope_org uuid;
    template_scope_user uuid;
    template_status VARCHAR(16);
BEGIN
    SELECT owner_organization_id, owner_user_id
    INTO exchange_owner_org, exchange_owner_user
    FROM exchange
    WHERE id = NEW.exchange_id;

    SELECT definition.scope_kind,
           definition.scope_org_id,
           definition.scope_user_id,
           version.status
    INTO template_scope_kind,
        template_scope_org,
        template_scope_user,
        template_status
    FROM information_request_template_version version
             JOIN information_request_template_definition definition
                  ON definition.id = version.template_definition_id
    WHERE version.id = NEW.template_version_id;

    IF template_status IS DISTINCT FROM 'PUBLISHED' THEN
        RAISE EXCEPTION 'an information request must pin a published template version';
    END IF;

    IF NEW.owner_type = 'ORGANIZATION' THEN
        IF exchange_owner_org IS DISTINCT FROM NEW.owner_organization_id
            OR exchange_owner_user IS NOT NULL
            OR template_scope_kind IS DISTINCT FROM 'ORGANIZATION'
            OR template_scope_org IS DISTINCT FROM NEW.owner_organization_id
        THEN
            RAISE EXCEPTION 'an information request owner must match its Exchange and Template owner';
        END IF;
    ELSE
        IF exchange_owner_user IS DISTINCT FROM NEW.owner_user_id
            OR exchange_owner_org IS NOT NULL
            OR template_scope_kind IS DISTINCT FROM 'PERSONAL'
            OR template_scope_user IS DISTINCT FROM NEW.owner_user_id
        THEN
            RAISE EXCEPTION 'an information request owner must match its Exchange and Template owner';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_owner_and_template_guard_write
    BEFORE INSERT OR UPDATE OF exchange_id, template_version_id, owner_type, owner_organization_id, owner_user_id
    ON information_request
    FOR EACH ROW
EXECUTE FUNCTION information_request_owner_and_template_guard();

CREATE TABLE information_request_party
(
    id                       uuid        NOT NULL,
    information_request_id   uuid        NOT NULL,
    role_key                 VARCHAR(32) NOT NULL,
    subject_identity_ref_id  uuid REFERENCES subject_identity_ref (id),
    principal_kind           VARCHAR(32),
    principal_id             uuid,
    exchange_recipient_id    uuid REFERENCES exchange_recipient (id),
    share_id                 uuid REFERENCES share (id),
    active                   BOOLEAN     NOT NULL DEFAULT TRUE,
    party_revision           BIGINT      NOT NULL DEFAULT 1,
    assigned_by_app_user_id  uuid REFERENCES app_user (id),
    assigned_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at               TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_party_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_party_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT ck_information_request_party_role CHECK (
        role_key IN ('SUBJECT', 'CONTRIBUTOR', 'PREPARER', 'ATTESTOR', 'REVIEWER', 'DECISION_MAKER')
        ),
    CONSTRAINT ck_information_request_party_principal_kind CHECK (
        principal_kind IS NULL OR principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP',
                                                     'ORGANIZATION', 'APPLICATION', 'SERVICE_ACCOUNT',
                                                     'PUBLIC_LINK')
        ),
    CONSTRAINT ck_information_request_party_subject CHECK (
        (role_key = 'SUBJECT' AND subject_identity_ref_id IS NOT NULL) OR
        (role_key <> 'SUBJECT' AND principal_kind IS NOT NULL AND principal_id IS NOT NULL)
        ),
    CONSTRAINT ck_information_request_party_revision CHECK (party_revision >= 1),
    CONSTRAINT ck_information_request_party_revocation CHECK (
        (active IS TRUE AND revoked_at IS NULL) OR active IS FALSE
        )
);

CREATE INDEX ix_information_request_party_request
    ON information_request_party (information_request_id, role_key, active);

CREATE INDEX ix_information_request_party_subject
    ON information_request_party (subject_identity_ref_id);

CREATE INDEX ix_information_request_party_principal
    ON information_request_party (principal_kind, principal_id);

CREATE TABLE information_request_requirement
(
    id                             uuid         NOT NULL,
    information_request_id         uuid         NOT NULL,
    source_template_version_id     uuid         NOT NULL,
    source_template_requirement_id uuid         NOT NULL,
    source_template_binding_id     uuid         NOT NULL,
    occurrence_path                VARCHAR(512) NOT NULL,
    created_at                     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_requirement_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_requirement_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT information_request_requirement_template_requirement_fkey
        FOREIGN KEY (source_template_requirement_id)
            REFERENCES information_request_template_requirement (id),
    CONSTRAINT information_request_requirement_template_binding_fkey
        FOREIGN KEY (source_template_binding_id, source_template_version_id)
            REFERENCES information_request_template_requirement_binding (id, template_version_id),
    CONSTRAINT ck_information_request_requirement_path CHECK (BTRIM(occurrence_path) <> ''),
    CONSTRAINT ux_information_request_requirement_occurrence
        UNIQUE (information_request_id, source_template_binding_id, occurrence_path),
    CONSTRAINT uq_information_request_requirement_source
        UNIQUE (id, information_request_id, source_template_binding_id)
);

CREATE INDEX ix_information_request_requirement_request
    ON information_request_requirement (information_request_id, occurrence_path);

CREATE FUNCTION information_request_requirement_source_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    request_template_version uuid;
    binding_requirement uuid;
BEGIN
    SELECT template_version_id
    INTO request_template_version
    FROM information_request
    WHERE id = NEW.information_request_id;

    IF request_template_version IS DISTINCT FROM NEW.source_template_version_id THEN
        RAISE EXCEPTION 'a runtime requirement must use the Template Version pinned by its request';
    END IF;

    SELECT template_requirement_id
    INTO binding_requirement
    FROM information_request_template_requirement_binding
    WHERE id = NEW.source_template_binding_id
      AND template_version_id = NEW.source_template_version_id;

    IF binding_requirement IS DISTINCT FROM NEW.source_template_requirement_id THEN
        RAISE EXCEPTION 'a runtime requirement must use the Template Requirement placed by its binding';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_requirement_source_guard_write
    BEFORE INSERT OR UPDATE
    ON information_request_requirement
    FOR EACH ROW
EXECUTE FUNCTION information_request_requirement_source_guard();

CREATE TABLE information_request_requirement_revision
(
    id                                 uuid         NOT NULL,
    information_request_requirement_id uuid         NOT NULL,
    information_request_id             uuid         NOT NULL,
    source_template_version_id         uuid         NOT NULL,
    source_template_requirement_id     uuid         NOT NULL,
    source_template_binding_id         uuid         NOT NULL,
    revision_number                    INTEGER      NOT NULL,
    occurrence_path                    VARCHAR(512) NOT NULL,
    effective_from                     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to                       TIMESTAMPTZ,
    configuration_hash_sha256          VARCHAR(64)  NOT NULL,
    optimistic_version                 BIGINT       NOT NULL DEFAULT 1,
    created_at                         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_requirement_revision_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_requirement_revision_requirement_fkey
        FOREIGN KEY (information_request_requirement_id, information_request_id, source_template_binding_id)
            REFERENCES information_request_requirement (id, information_request_id, source_template_binding_id),
    CONSTRAINT info_request_requirement_revision_template_req_fkey
        FOREIGN KEY (source_template_requirement_id)
            REFERENCES information_request_template_requirement (id),
    CONSTRAINT information_request_requirement_revision_template_binding_fkey
        FOREIGN KEY (source_template_binding_id, source_template_version_id)
            REFERENCES information_request_template_requirement_binding (id, template_version_id),
    CONSTRAINT ck_information_request_requirement_revision_number CHECK (revision_number >= 1),
    CONSTRAINT ck_information_request_requirement_revision_path CHECK (BTRIM(occurrence_path) <> ''),
    CONSTRAINT ck_information_request_requirement_revision_hash CHECK (
        configuration_hash_sha256 ~ '^[0-9a-f]{64}$'
        ),
    CONSTRAINT ck_information_request_requirement_revision_optimistic CHECK (optimistic_version >= 1),
    CONSTRAINT ck_information_request_requirement_revision_interval CHECK (
        effective_to IS NULL OR effective_to > effective_from
        ),
    CONSTRAINT ux_information_request_requirement_revision_number
        UNIQUE (information_request_requirement_id, revision_number),
    CONSTRAINT uq_information_request_requirement_revision_current
        UNIQUE (id, information_request_requirement_id, revision_number)
);

CREATE INDEX ix_information_request_requirement_revision_request
    ON information_request_requirement_revision (information_request_id, effective_from);

CREATE TABLE information_request_requirement_current
(
    information_request_requirement_id uuid        NOT NULL,
    current_revision_id                uuid        NOT NULL,
    current_revision_number            INTEGER     NOT NULL,
    updated_at                         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_requirement_current_pkey PRIMARY KEY (information_request_requirement_id),
    CONSTRAINT information_request_requirement_current_requirement_fkey
        FOREIGN KEY (information_request_requirement_id)
            REFERENCES information_request_requirement (id),
    CONSTRAINT information_request_requirement_current_revision_fkey
        FOREIGN KEY (current_revision_id, information_request_requirement_id, current_revision_number)
            REFERENCES information_request_requirement_revision
                (id, information_request_requirement_id, revision_number),
    CONSTRAINT ux_information_request_requirement_current_revision UNIQUE (current_revision_id),
    CONSTRAINT ck_information_request_requirement_current_revision CHECK (current_revision_number >= 1)
);

CREATE TABLE information_request_transition
(
    id                     uuid        NOT NULL,
    information_request_id uuid        NOT NULL,
    sequence_number        INTEGER     NOT NULL,
    from_state             VARCHAR(32),
    to_state               VARCHAR(32) NOT NULL,
    mutation               VARCHAR(64) NOT NULL,
    actor_kind             VARCHAR(32) NOT NULL,
    actor_id               uuid        NOT NULL,
    reason_code            VARCHAR(128),
    command_receipt_id     uuid REFERENCES command_receipt (id),
    occurred_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_transition_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_transition_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT ck_information_request_transition_sequence CHECK (sequence_number >= 1),
    CONSTRAINT ck_information_request_transition_state CHECK (
        (from_state IS NULL OR from_state IN ('DRAFT', 'ISSUED', 'IN_PROGRESS', 'SUBMITTED',
                                             'UNDER_REVIEW', 'CHANGES_REQUESTED', 'CLOSED',
                                             'CANCELLED', 'SUPERSEDED', 'EXPIRED')) AND
        to_state IN ('DRAFT', 'ISSUED', 'IN_PROGRESS', 'SUBMITTED', 'UNDER_REVIEW',
                     'CHANGES_REQUESTED', 'CLOSED', 'CANCELLED', 'SUPERSEDED', 'EXPIRED')
        ),
    CONSTRAINT ck_information_request_transition_mutation CHECK (
        mutation IN ('CREATE_DRAFT', 'ISSUE', 'RECORD_FIRST_VIEW', 'SAVE_RESPONSE',
                     'ATTEST_RESPONSE', 'ADMINISTER_EVIDENCE', 'SUBMIT', 'START_REVIEW',
                     'REQUEST_CORRECTION', 'CLOSE', 'AMEND', 'REASSIGN', 'CANCEL',
                     'SUPERSEDE', 'EXPIRE')
        ),
    CONSTRAINT ck_information_request_transition_actor CHECK (
        actor_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION', 'APPLICATION',
                       'SERVICE_ACCOUNT', 'PUBLIC_LINK', 'ACCESS_SESSION')
        ),
    CONSTRAINT ck_information_request_transition_reason CHECK (
        reason_code IS NULL OR BTRIM(reason_code) <> ''
        ),
    CONSTRAINT ux_information_request_transition_sequence UNIQUE (information_request_id, sequence_number)
);

CREATE INDEX ix_information_request_transition_request
    ON information_request_transition (information_request_id, occurred_at);

CREATE FUNCTION information_request_append_only_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    RAISE EXCEPTION 'information request history is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_requirement_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_requirement
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER information_request_requirement_revision_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_requirement_revision
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER information_request_transition_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_transition
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();
