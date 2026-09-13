ALTER TABLE share
    DROP CONSTRAINT IF EXISTS share_resource_type_check,
    DROP CONSTRAINT IF EXISTS share_role_name_check;

ALTER TABLE share
    ALTER COLUMN resource_type TYPE VARCHAR(64);

ALTER TABLE share
    ADD CONSTRAINT share_resource_type_check
        CHECK (resource_type IN ('EXCHANGE', 'DOCUMENT', 'PRINCIPAL_GROUP', 'INFORMATION_REQUEST')),
    ADD CONSTRAINT share_role_name_check
        CHECK (
            (
                resource_type IN ('EXCHANGE', 'DOCUMENT', 'PRINCIPAL_GROUP')
                AND role_name IN ('OWNER', 'EDITOR', 'REVIEWER', 'SIGNER', 'VIEWER', 'COMMENTER', 'PARTICIPANT')
            )
            OR (
                resource_type = 'INFORMATION_REQUEST'
                AND role_name IN ('SUBJECT', 'CONTRIBUTOR', 'PREPARER', 'ATTESTOR', 'REVIEWER', 'DECISION_MAKER')
            )
        );
