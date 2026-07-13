DROP TABLE IF EXISTS auth_audit_event;
DROP TABLE IF EXISTS access_audit_log;
DROP TABLE IF EXISTS audit_log;

ALTER TABLE audit_archive_segment
    DROP COLUMN IF EXISTS legacy_import;
