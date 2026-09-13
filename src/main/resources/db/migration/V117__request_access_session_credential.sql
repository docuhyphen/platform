ALTER TABLE request_access_session ADD COLUMN credential_hash VARCHAR(64);

UPDATE request_access_session SET revoked_at = COALESCE(revoked_at, now());

ALTER TABLE request_access_session ADD CONSTRAINT ck_request_access_session_credential
    CHECK (credential_hash IS NULL OR (credential_hash ~ '^[0-9a-f]{64}$' AND expires_at IS NOT NULL));

CREATE UNIQUE INDEX ux_request_access_session_credential
    ON request_access_session (credential_hash) WHERE credential_hash IS NOT NULL;

CREATE FUNCTION guard_request_access_session_credential() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.credential_hash IS NULL THEN
        NEW.revoked_at := COALESCE(NEW.revoked_at, now());
    END IF;
    IF TG_OP = 'UPDATE' AND (
        NEW.share_link_id IS DISTINCT FROM OLD.share_link_id OR
        NEW.participant_principal_kind IS DISTINCT FROM OLD.participant_principal_kind OR
        NEW.participant_principal_id IS DISTINCT FROM OLD.participant_principal_id OR
        NEW.credential_hash IS DISTINCT FROM OLD.credential_hash
    ) THEN
        RAISE EXCEPTION 'Request access session identity is immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER request_access_session_credential_guard
    BEFORE INSERT OR UPDATE ON request_access_session
    FOR EACH ROW EXECUTE FUNCTION guard_request_access_session_credential();
