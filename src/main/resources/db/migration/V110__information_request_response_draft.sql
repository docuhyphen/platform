ALTER TABLE information_request
    ADD COLUMN response_revision BIGINT NOT NULL DEFAULT 1;

ALTER TABLE information_request
    ADD CONSTRAINT ck_information_request_response_revision CHECK (response_revision >= 1);

CREATE TABLE information_request_response
(
    id                                 uuid         NOT NULL,
    information_request_id             uuid         NOT NULL,
    information_request_requirement_id uuid         NOT NULL,
    requirement_revision_id            uuid         NOT NULL,
    occurrence_path                    VARCHAR(512) NOT NULL,
    disposition                        VARCHAR(32)  NOT NULL DEFAULT 'NOT_ANSWERED',
    narrative                          text,
    field_value_set_id                 uuid REFERENCES field_value_set (id),
    response_revision                  BIGINT       NOT NULL DEFAULT 1,
    recorded_by_principal_kind         VARCHAR(32)  NOT NULL,
    recorded_by_principal_id           uuid         NOT NULL,
    recorded_by_session_ref            VARCHAR(64),
    created_at                         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_response_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_response_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT information_request_response_requirement_fkey
        FOREIGN KEY (information_request_requirement_id) REFERENCES information_request_requirement (id),
    CONSTRAINT information_request_response_requirement_revision_fkey
        FOREIGN KEY (requirement_revision_id) REFERENCES information_request_requirement_revision (id),
    CONSTRAINT ck_information_request_response_path CHECK (BTRIM(occurrence_path) <> ''),
    CONSTRAINT ck_information_request_response_disposition CHECK (
        disposition IN ('NOT_ANSWERED', 'PROVIDED', 'PARTIALLY_PROVIDED', 'NOT_APPLICABLE',
                        'UNAVAILABLE', 'EXCEPTION_REQUESTED', 'SATISFIED_BY_REFERENCE', 'WAIVED')
        ),
    CONSTRAINT ck_information_request_response_revision CHECK (response_revision >= 1),
    CONSTRAINT ck_information_request_response_actor CHECK (
        recorded_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                       'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        ),
    CONSTRAINT ux_information_request_response_current
        UNIQUE (information_request_id, information_request_requirement_id)
);

CREATE INDEX ix_information_request_response_request
    ON information_request_response (information_request_id, occurrence_path);

CREATE INDEX ix_information_request_response_requirement
    ON information_request_response (information_request_requirement_id, response_revision);

CREATE FUNCTION information_request_response_scope_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    requirement_request_id uuid;
    requirement_path VARCHAR(512);
    revision_requirement_id uuid;
    revision_request_id uuid;
    revision_path VARCHAR(512);
BEGIN
    SELECT information_request_id, occurrence_path
    INTO requirement_request_id, requirement_path
    FROM information_request_requirement
    WHERE id = NEW.information_request_requirement_id;

    SELECT information_request_requirement_id, information_request_id, occurrence_path
    INTO revision_requirement_id, revision_request_id, revision_path
    FROM information_request_requirement_revision
    WHERE id = NEW.requirement_revision_id;

    IF requirement_request_id IS DISTINCT FROM NEW.information_request_id
        OR revision_request_id IS DISTINCT FROM NEW.information_request_id
        OR revision_requirement_id IS DISTINCT FROM NEW.information_request_requirement_id
        OR requirement_path IS DISTINCT FROM NEW.occurrence_path
        OR revision_path IS DISTINCT FROM NEW.occurrence_path
    THEN
        RAISE EXCEPTION 'an Information Request response must match one Requirement occurrence and its current revision';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_response_scope_guard_write
    BEFORE INSERT OR UPDATE OF information_request_id, information_request_requirement_id,
        requirement_revision_id, occurrence_path
    ON information_request_response
    FOR EACH ROW
EXECUTE FUNCTION information_request_response_scope_guard();
