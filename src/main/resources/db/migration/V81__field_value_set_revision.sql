-- A set of answers counts the changes it has been through.
--
-- A client that edits a set of answers has to be able to say which state of that set it edited, so
-- a save can be refused when the answers moved on underneath it. The released schema gives the set
-- nothing durable to name: it carries a timestamp, which two changes inside the same clock tick
-- share and which a clock correction can move backwards, so a stale claim can look current.
--
-- A count of recorded changes is the missing fact. It starts at the set's first state, moves by one
-- for each save that stores something, and is refused if it is ever asked to move backwards, which
-- is what makes a validator derived from it strong.

ALTER TABLE field_value_set
    -- Every set that exists has been through exactly one state, so the default also carries the
    -- rows already stored. The default is kept rather than dropped so an application version that
    -- predates this column can still create a set during a rolling deploy.
    ADD COLUMN revision bigint NOT NULL DEFAULT 1;

ALTER TABLE field_value_set
    -- A set that has never changed is still in its first state; there is no state before that.
    ADD CONSTRAINT ck_field_value_set_revision CHECK (revision >= 1);

-- A count that could move backwards would let a validator already handed out become current again,
-- which is exactly the confusion the count exists to prevent. Rewriting the set without touching
-- its count stays an ordinary update.
CREATE OR REPLACE FUNCTION field_value_set_revision_is_monotonic()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.revision < OLD.revision THEN
        RAISE EXCEPTION 'field_value_set revision cannot move backwards (% to %)', OLD.revision, NEW.revision
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_field_value_set_revision_monotonic
    BEFORE UPDATE ON field_value_set
    FOR EACH ROW
    EXECUTE FUNCTION field_value_set_revision_is_monotonic();
