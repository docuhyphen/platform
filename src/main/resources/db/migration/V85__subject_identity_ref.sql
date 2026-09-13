CREATE TABLE subject_identity_ref
(
    id UUID PRIMARY KEY,
    owner_type VARCHAR(32) NOT NULL,
    owner_organization_id UUID REFERENCES organization(id),
    owner_user_id UUID REFERENCES app_user(id),
    owner_id UUID GENERATED ALWAYS AS (COALESCE(owner_organization_id, owner_user_id)) STORED,
    subject_kind VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_subject_identity_ref_owner CHECK (
        (owner_type = 'ORGANIZATION' AND owner_organization_id IS NOT NULL AND owner_user_id IS NULL)
        OR (owner_type = 'USER' AND owner_organization_id IS NULL AND owner_user_id IS NOT NULL)
    ),
    CONSTRAINT ck_subject_identity_ref_subject_kind CHECK (
        subject_kind IN ('PERSON', 'ORGANIZATION', 'ASSET', 'RECORD', 'OTHER')
    ),
    CONSTRAINT uq_subject_identity_ref_tenant UNIQUE (id, owner_type, owner_id)
);

CREATE INDEX idx_subject_identity_ref_tenant
    ON subject_identity_ref (owner_type, owner_id, subject_kind, created_at);

CREATE TABLE subject_identity_external_identifier
(
    id UUID PRIMARY KEY,
    subject_identity_ref_id UUID NOT NULL,
    owner_type VARCHAR(32) NOT NULL,
    owner_id UUID NOT NULL,
    authority VARCHAR(160) NOT NULL,
    identifier_type VARCHAR(96) NOT NULL,
    identifier_value VARCHAR(512) NOT NULL,
    authorized_by_principal_kind VARCHAR(32) NOT NULL,
    authorized_by_principal_id UUID NOT NULL,
    authorized_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_subject_identity_external_identifier_owner_type CHECK (
        owner_type IN ('ORGANIZATION', 'USER')
    ),
    CONSTRAINT ck_subject_identity_external_identifier_actor_kind CHECK (
        authorized_by_principal_kind IN (
            'USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
            'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK'
        )
    ),
    CONSTRAINT ck_subject_identity_external_identifier_values CHECK (
        BTRIM(authority) <> '' AND BTRIM(identifier_type) <> '' AND BTRIM(identifier_value) <> ''
    ),
    CONSTRAINT subject_identity_external_identifier_subject_fkey
        FOREIGN KEY (subject_identity_ref_id, owner_type, owner_id)
        REFERENCES subject_identity_ref (id, owner_type, owner_id),
    CONSTRAINT ux_subject_identity_external_identifier_tenant_value
        UNIQUE (owner_type, owner_id, authority, identifier_type, identifier_value)
);

CREATE INDEX idx_subject_identity_external_identifier_subject
    ON subject_identity_external_identifier (subject_identity_ref_id, authorized_at);

CREATE TABLE subject_identity_transition
(
    id UUID PRIMARY KEY,
    source_subject_identity_id UUID NOT NULL,
    target_subject_identity_id UUID NOT NULL,
    owner_type VARCHAR(32) NOT NULL,
    owner_id UUID NOT NULL,
    transition_kind VARCHAR(32) NOT NULL,
    reason VARCHAR(1000),
    recorded_by_principal_kind VARCHAR(32) NOT NULL,
    recorded_by_principal_id UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_subject_identity_transition_owner_type CHECK (
        owner_type IN ('ORGANIZATION', 'USER')
    ),
    CONSTRAINT ck_subject_identity_transition_kind CHECK (
        transition_kind IN ('MERGED', 'SUPERSEDED')
    ),
    CONSTRAINT ck_subject_identity_transition_distinct CHECK (
        source_subject_identity_id <> target_subject_identity_id
    ),
    CONSTRAINT ck_subject_identity_transition_actor_kind CHECK (
        recorded_by_principal_kind IN (
            'USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
            'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK'
        )
    ),
    CONSTRAINT subject_identity_transition_source_fkey
        FOREIGN KEY (source_subject_identity_id, owner_type, owner_id)
        REFERENCES subject_identity_ref (id, owner_type, owner_id),
    CONSTRAINT subject_identity_transition_target_fkey
        FOREIGN KEY (target_subject_identity_id, owner_type, owner_id)
        REFERENCES subject_identity_ref (id, owner_type, owner_id),
    CONSTRAINT ux_subject_identity_transition_source UNIQUE (source_subject_identity_id)
);

CREATE INDEX idx_subject_identity_transition_target
    ON subject_identity_transition (target_subject_identity_id, recorded_at);

CREATE FUNCTION subject_identity_transition_guard()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        WITH RECURSIVE successor(subject_id) AS (
            SELECT NEW.target_subject_identity_id
            UNION ALL
            SELECT transition.target_subject_identity_id
            FROM subject_identity_transition transition
            JOIN successor ON transition.source_subject_identity_id = successor.subject_id
        )
        SELECT 1
        FROM successor
        WHERE subject_id = NEW.source_subject_identity_id
    ) THEN
        RAISE EXCEPTION 'subject identity transition cycle';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER subject_identity_transition_guard_insert
BEFORE INSERT ON subject_identity_transition
FOR EACH ROW EXECUTE FUNCTION subject_identity_transition_guard();

CREATE FUNCTION subject_identity_transition_deny_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'subject_identity_transition is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER subject_identity_transition_deny_update
BEFORE UPDATE OR DELETE ON subject_identity_transition
FOR EACH ROW EXECUTE FUNCTION subject_identity_transition_deny_mutation();
