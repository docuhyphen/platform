ALTER TABLE information_request_group_occurrence
    ADD COLUMN removed_at TIMESTAMPTZ,
    ADD COLUMN removed_by_principal_kind VARCHAR(32),
    ADD COLUMN removed_by_principal_id uuid;

ALTER TABLE information_request_group_occurrence
    ADD CONSTRAINT ck_request_group_occurrence_removed_actor CHECK (
        (
            removed_at IS NULL
                AND removed_by_principal_kind IS NULL
                AND removed_by_principal_id IS NULL
            ) OR (
            removed_at IS NOT NULL
                AND removed_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                                  'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
                AND removed_by_principal_id IS NOT NULL
            )
        );

CREATE INDEX ix_request_group_occurrence_active_group_parent
    ON information_request_group_occurrence (information_request_id, source_template_group_id, parent_occurrence_id)
    WHERE removed_at IS NULL;
