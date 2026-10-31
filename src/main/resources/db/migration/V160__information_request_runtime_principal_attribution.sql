ALTER TABLE information_request
    ADD COLUMN created_by_principal_kind VARCHAR(32),
    ADD COLUMN created_by_principal_id uuid;

UPDATE information_request
SET created_by_principal_kind = 'USER',
    created_by_principal_id   = created_by_app_user_id
WHERE created_by_app_user_id IS NOT NULL;

UPDATE information_request request
SET created_by_principal_kind = creation.actor_kind,
    created_by_principal_id   = creation.actor_id FROM (SELECT DISTINCT ON (information_request_id) information_request_id, actor_kind, actor_id
      FROM information_request_transition
      WHERE mutation = 'CREATE_DRAFT'
        AND actor_kind <> 'ACCESS_SESSION'
      ORDER BY information_request_id, sequence_number) creation
WHERE request.created_by_principal_kind IS NULL
  AND creation.information_request_id = request.id;

ALTER TABLE information_request
DROP
COLUMN created_by_app_user_id,
    ADD CONSTRAINT ck_information_request_created_principal CHECK (
        (created_by_principal_kind IS NULL AND created_by_principal_id IS NULL)
        OR (created_by_principal_kind IS NOT NULL
            AND created_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION', 'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
            AND created_by_principal_id IS NOT NULL)
    );

ALTER TABLE information_request_party
    ADD COLUMN assigned_by_principal_kind VARCHAR(32),
    ADD COLUMN assigned_by_principal_id uuid;

UPDATE information_request_party
SET assigned_by_principal_kind = 'USER',
    assigned_by_principal_id   = assigned_by_app_user_id
WHERE assigned_by_app_user_id IS NOT NULL;

UPDATE information_request_party party
SET assigned_by_principal_kind = assignment.actor_kind,
    assigned_by_principal_id   = assignment.actor_id FROM (SELECT DISTINCT ON (party_id) party_id, actor_kind, actor_id
      FROM information_request_transition
      WHERE party_id IS NOT NULL
        AND mutation IN ('ASSIGN_PARTY', 'REASSIGN')
        AND actor_kind <> 'ACCESS_SESSION'
      ORDER BY party_id, sequence_number DESC) assignment
WHERE party.assigned_by_principal_kind IS NULL
  AND assignment.party_id = party.id;

ALTER TABLE information_request_party
DROP
COLUMN assigned_by_app_user_id,
    ADD CONSTRAINT ck_information_request_party_assigned_principal CHECK (
        (assigned_by_principal_kind IS NULL AND assigned_by_principal_id IS NULL)
        OR (assigned_by_principal_kind IS NOT NULL
            AND assigned_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION', 'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
            AND assigned_by_principal_id IS NOT NULL)
    );
