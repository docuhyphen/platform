ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS is_password_temporary BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS temporary_password_expires_at TIMESTAMP NULL;

