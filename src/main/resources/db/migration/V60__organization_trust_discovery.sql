ALTER TABLE organization_settings
    ADD COLUMN discoverable_for_trust_requests BOOLEAN NOT NULL DEFAULT FALSE;
