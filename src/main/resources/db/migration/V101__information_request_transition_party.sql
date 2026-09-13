-- Party reassignment now writes an append-only transition row alongside its Share changes. The
-- party reference lets history and audit queries scope to one request party rather than only the
-- request aggregate.

ALTER TABLE information_request_transition
    ADD COLUMN party_id uuid REFERENCES information_request_party (id);

CREATE INDEX ix_information_request_transition_party
    ON information_request_transition (party_id);

CREATE FUNCTION information_request_transition_party_scope_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    party_request uuid;
BEGIN
    IF NEW.party_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT information_request_id
    INTO party_request
    FROM information_request_party
    WHERE id = NEW.party_id;

    IF party_request IS DISTINCT FROM NEW.information_request_id THEN
        RAISE EXCEPTION 'transition party must belong to its Information Request';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_transition_party_scope_guard_write
    BEFORE INSERT
    ON information_request_transition
    FOR EACH ROW
EXECUTE FUNCTION information_request_transition_party_scope_guard();
