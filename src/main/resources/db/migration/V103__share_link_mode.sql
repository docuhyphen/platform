ALTER TABLE share_link
    ADD COLUMN link_mode VARCHAR(32) NOT NULL DEFAULT 'DIRECT_GRANT';

ALTER TABLE share_link
    ALTER COLUMN link_mode DROP DEFAULT;

ALTER TABLE share_link
    ADD CONSTRAINT share_link_link_mode_check
        CHECK (link_mode IN ('DIRECT_GRANT', 'VERIFICATION_BOOTSTRAP'));
