ALTER TABLE workflow_event_outbox
DROP
CONSTRAINT ck_workflow_event_outbox_owner;

DROP INDEX idx_workflow_event_outbox_organization_id;

ALTER TABLE workflow_event_outbox
DROP
COLUMN organization_id;

ALTER TABLE workflow_event_outbox
    ADD CONSTRAINT ck_workflow_event_outbox_owner CHECK (
        (owner_kind = 'PLATFORM' AND owner_id IS NULL)
            OR (owner_kind IN ('ORGANIZATION', 'USER') AND owner_id IS NOT NULL)
        );

DELETE
FROM request_access_session
WHERE credential_hash IS NULL
   OR expires_at IS NULL;

DROP TRIGGER request_access_session_credential_guard ON request_access_session;

DROP INDEX ux_request_access_session_credential;

ALTER TABLE request_access_session
DROP
CONSTRAINT ck_request_access_session_credential;

ALTER TABLE request_access_session
    ALTER COLUMN credential_hash SET NOT NULL,
ALTER
COLUMN expires_at SET NOT NULL,
    ADD CONSTRAINT ck_request_access_session_credential CHECK (credential_hash ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT uq_request_access_session_credential UNIQUE (credential_hash);

CREATE
OR REPLACE FUNCTION guard_request_access_session_credential() RETURNS TRIGGER AS $$
BEGIN
    IF
NEW.share_link_id IS DISTINCT FROM OLD.share_link_id OR
       NEW.participant_principal_kind IS DISTINCT FROM OLD.participant_principal_kind OR
       NEW.participant_principal_id IS DISTINCT FROM OLD.participant_principal_id OR
       NEW.credential_hash IS DISTINCT FROM OLD.credential_hash THEN
        RAISE EXCEPTION 'Request access session identity is immutable';
END IF;
RETURN NEW;
END;
$$
LANGUAGE plpgsql;

CREATE TRIGGER request_access_session_credential_guard
    BEFORE UPDATE
    ON request_access_session
    FOR EACH ROW EXECUTE FUNCTION guard_request_access_session_credential();
