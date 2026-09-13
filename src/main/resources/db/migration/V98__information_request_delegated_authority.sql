-- Delegated authority rows feed Requirement authorization facts. The full authority instrument,
-- expiry, revocation history, and evidence lifecycle are added by the later authority model task.

CREATE TABLE information_request_delegated_authority
(
    id                      uuid        NOT NULL,
    information_request_id  uuid        NOT NULL,
    assigned_party_id       uuid        NOT NULL,
    delegate_principal_kind VARCHAR(32) NOT NULL,
    delegate_principal_id   uuid        NOT NULL,
    requirement_id          uuid,
    active                  BOOLEAN     NOT NULL DEFAULT TRUE,
    recorded_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_delegated_authority_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_delegated_authority_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT information_request_delegated_authority_party_fkey
        FOREIGN KEY (assigned_party_id) REFERENCES information_request_party (id),
    CONSTRAINT information_request_delegated_authority_requirement_fkey
        FOREIGN KEY (requirement_id) REFERENCES information_request_requirement (id),
    CONSTRAINT ck_information_request_delegated_authority_principal CHECK (
        delegate_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP')
        )
);

CREATE INDEX ix_information_request_delegated_authority_request
    ON information_request_delegated_authority (information_request_id, active);

CREATE INDEX ix_information_request_delegated_authority_party
    ON information_request_delegated_authority (assigned_party_id, active);

CREATE INDEX ix_information_request_delegated_authority_delegate
    ON information_request_delegated_authority (delegate_principal_kind, delegate_principal_id, active);

CREATE FUNCTION information_request_delegated_authority_scope_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    party_request uuid;
    requirement_request uuid;
BEGIN
    SELECT information_request_id
    INTO party_request
    FROM information_request_party
    WHERE id = NEW.assigned_party_id;

    IF party_request IS DISTINCT FROM NEW.information_request_id THEN
        RAISE EXCEPTION 'delegated authority assigned party must belong to its Information Request';
    END IF;

    IF NEW.requirement_id IS NOT NULL THEN
        SELECT information_request_id
        INTO requirement_request
        FROM information_request_requirement
        WHERE id = NEW.requirement_id;

        IF requirement_request IS DISTINCT FROM NEW.information_request_id THEN
            RAISE EXCEPTION 'delegated authority Requirement scope must belong to its Information Request';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_delegated_authority_scope_guard_write
    BEFORE INSERT OR UPDATE OF information_request_id, assigned_party_id, requirement_id
    ON information_request_delegated_authority
    FOR EACH ROW
EXECUTE FUNCTION information_request_delegated_authority_scope_guard();
