ALTER TABLE share
DROP
CONSTRAINT share_role_name_check;

ALTER TABLE share
    ADD CONSTRAINT share_role_name_check
        CHECK (
            (
                resource_type IN ('EXCHANGE', 'DOCUMENT', 'PRINCIPAL_GROUP')
                    AND role_name IN ('OWNER', 'EDITOR', 'REVIEWER', 'SIGNER', 'VIEWER', 'COMMENTER', 'PARTICIPANT')
                )
                OR (
                resource_type = 'INFORMATION_REQUEST'
                    AND
                role_name IN ('CONTRIBUTOR', 'PREPARER', 'ATTESTOR', 'REVIEWER', 'DECISION_MAKER', 'RECORD_OWNER')
                )
            );
