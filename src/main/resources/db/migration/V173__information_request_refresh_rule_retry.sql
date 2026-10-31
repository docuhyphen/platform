ALTER TABLE information_request_refresh_rule
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMPTZ;

ALTER TABLE information_request_refresh_rule
    ADD CONSTRAINT ck_information_request_refresh_rule_attempt_count CHECK (attempt_count >= 0),
    ADD CONSTRAINT ck_information_request_refresh_rule_retry CHECK ((attempt_count = 0) = (next_attempt_at IS NULL));

CREATE FUNCTION information_request_refresh_rule_guard()
    RETURNS TRIGGER AS
    $$
BEGIN
    IF
TG_OP = 'DELETE' AND record_disposal_in_progress() THEN
        RETURN OLD;
END IF;
    IF
TG_OP = 'UPDATE' AND
       ROW (NEW.id, NEW.information_request_id, NEW.requirement_key, NEW.lead_days,
            NEW.created_by_principal_kind, NEW.created_by_principal_id, NEW.created_at)
           IS NOT DISTINCT FROM
       ROW (OLD.id, OLD.information_request_id, OLD.requirement_key, OLD.lead_days,
            OLD.created_by_principal_kind, OLD.created_by_principal_id, OLD.created_at) THEN
        RETURN NEW;
END IF;
    RAISE
EXCEPTION 'information request refresh rules change only their retry state';
END;
$$
LANGUAGE plpgsql;

DROP TRIGGER information_request_refresh_rule_append_only ON information_request_refresh_rule;

CREATE TRIGGER information_request_refresh_rule_guard
    BEFORE UPDATE OR
DELETE
ON information_request_refresh_rule
    FOR EACH ROW
EXECUTE FUNCTION information_request_refresh_rule_guard();
