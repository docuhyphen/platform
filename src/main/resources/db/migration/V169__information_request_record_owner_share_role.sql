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
                    AND role_name IN
                        ('SUBJECT', 'CONTRIBUTOR', 'PREPARER', 'ATTESTOR', 'REVIEWER', 'DECISION_MAKER', 'RECORD_OWNER')
                )
            );

UPDATE share
SET role_name = 'RECORD_OWNER'
WHERE resource_type = 'INFORMATION_REQUEST'
  AND role_name = 'SUBJECT'
  AND NOT EXISTS (SELECT 1 FROM information_request_party party WHERE party.share_id = share.id);
