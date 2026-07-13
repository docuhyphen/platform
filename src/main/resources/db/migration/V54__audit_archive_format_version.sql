ALTER TABLE audit_archive_segment
    ADD COLUMN format_version INTEGER NOT NULL DEFAULT 1;
