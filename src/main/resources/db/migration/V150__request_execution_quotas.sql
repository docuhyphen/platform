ALTER TABLE request_execution_grant RENAME COLUMN additional_recipient_cap TO acting_party_cap;
ALTER TABLE request_execution_grant ADD COLUMN evidence_file_allowance BIGINT;
ALTER TABLE request_execution_grant ADD COLUMN evidence_byte_allowance BIGINT;
ALTER TABLE request_execution_grant
    ADD CONSTRAINT ck_request_execution_grant_allowances CHECK (
        (acting_party_cap IS NULL OR acting_party_cap >= 0) AND
        (evidence_file_allowance IS NULL OR evidence_file_allowance >= 0) AND
        (evidence_byte_allowance IS NULL OR evidence_byte_allowance >= 0)
        );

ALTER TABLE request_execution_usage_reservation DROP CONSTRAINT ck_request_execution_usage_reservation_usage_kind;
UPDATE request_execution_usage_reservation
SET usage_kind = 'ACTING_PARTY'
WHERE usage_kind = 'ADDITIONAL_RECIPIENT';
ALTER TABLE request_execution_usage_reservation
    ADD CONSTRAINT ck_request_execution_usage_reservation_usage_kind CHECK (
        usage_kind IN ('ACTING_PARTY')
        );

CREATE FUNCTION request_execution_grant_frozen() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF ROW (NEW.id, NEW.request_id, NEW.owner_type, NEW.owner_organization_id, NEW.owner_user_id,
            NEW.plan_code, NEW.subscription_status, NEW.enforcement_mode, NEW.trial_expires_at,
            NEW.mutation_allowance_expires_at, NEW.acting_party_cap, NEW.evidence_file_allowance,
            NEW.evidence_byte_allowance, NEW.issued_at, NEW.created_at)
        IS DISTINCT FROM
       ROW (OLD.id, OLD.request_id, OLD.owner_type, OLD.owner_organization_id, OLD.owner_user_id,
            OLD.plan_code, OLD.subscription_status, OLD.enforcement_mode, OLD.trial_expires_at,
            OLD.mutation_allowance_expires_at, OLD.acting_party_cap, OLD.evidence_file_allowance,
            OLD.evidence_byte_allowance, OLD.issued_at, OLD.created_at)
    THEN
        RAISE EXCEPTION 'an execution grant keeps the position it was issued under'
            USING ERRCODE = 'check_violation';
    END IF;
    IF OLD.revoked_at IS NOT NULL
        AND ROW (NEW.revoked_at, NEW.revoked_reason) IS DISTINCT FROM ROW (OLD.revoked_at, OLD.revoked_reason)
    THEN
        RAISE EXCEPTION 'an execution grant is revoked once'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER request_execution_grant_frozen
    BEFORE UPDATE
    ON request_execution_grant
    FOR EACH ROW
EXECUTE FUNCTION request_execution_grant_frozen();
