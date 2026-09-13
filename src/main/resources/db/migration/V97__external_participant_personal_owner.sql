-- External participants now belong either to one organization directory or one personal address
-- book. Rows created before ownership existed carry no owner, so they are adopted into the owner
-- of the Exchange they were first shared into before the ownership rule is enforced.

ALTER TABLE external_participant
    ADD COLUMN owner_app_user_id uuid;

ALTER TABLE external_participant
    ADD CONSTRAINT fk_external_participant_owner_app_user
        FOREIGN KEY (owner_app_user_id) REFERENCES app_user (id);

DROP INDEX IF EXISTS uq_external_participant_org_email;

CREATE TEMP TABLE legacy_participant_owner ON COMMIT DROP AS
SELECT DISTINCT ON (s.principal_id) s.principal_id          AS participant_id,
                                    e.owner_organization_id AS target_organization_id,
                                    CASE
                                        WHEN e.owner_organization_id IS NULL
                                            THEN COALESCE(e.owner_user_id, e.initiator_id)
                                        END                 AS target_app_user_id
FROM share s
    JOIN exchange e ON e.id = s.resource_id
    JOIN external_participant p ON p.id = s.principal_id
WHERE s.principal_kind = 'PARTICIPANT'
  AND s.resource_type = 'EXCHANGE'
  AND p.owner_organization_id IS NULL
  AND p.owner_app_user_id IS NULL
ORDER BY s.principal_id, s.granted_at;

DELETE
FROM legacy_participant_owner
WHERE target_organization_id IS NULL
  AND target_app_user_id IS NULL;

-- An ownerless row whose derived owner already has a directory entry for the same email duplicates
-- that entry, so its principal references are moved onto the surviving entry instead.
CREATE TEMP TABLE legacy_participant_duplicate ON COMMIT DROP AS
SELECT l.participant_id, survivor.id AS survivor_id
FROM legacy_participant_owner l
    JOIN external_participant legacy ON legacy.id = l.participant_id
    JOIN external_participant survivor
         ON survivor.id <> legacy.id
             AND survivor.email_lower = legacy.email_lower
             AND survivor.owner_organization_id IS NOT DISTINCT FROM l.target_organization_id
             AND survivor.owner_app_user_id IS NOT DISTINCT FROM l.target_app_user_id;

UPDATE share s
SET principal_id = d.survivor_id
FROM legacy_participant_duplicate d
WHERE s.principal_kind = 'PARTICIPANT'
  AND s.principal_id = d.participant_id;

DELETE
FROM principal_group_member m
    USING legacy_participant_duplicate d
WHERE m.principal_kind = 'PARTICIPANT'
  AND m.principal_id = d.participant_id
  AND EXISTS (SELECT 1
              FROM principal_group_member existing
              WHERE existing.principal_group_id = m.principal_group_id
                AND existing.principal_kind = 'PARTICIPANT'
                AND existing.principal_id = d.survivor_id);

UPDATE principal_group_member m
SET principal_id = d.survivor_id
FROM legacy_participant_duplicate d
WHERE m.principal_kind = 'PARTICIPANT'
  AND m.principal_id = d.participant_id;

UPDATE external_participant p
SET owner_organization_id = l.target_organization_id,
    owner_app_user_id     = l.target_app_user_id
FROM legacy_participant_owner l
WHERE p.id = l.participant_id
  AND p.owner_organization_id IS NULL
  AND p.owner_app_user_id IS NULL
  AND NOT EXISTS (SELECT 1
                  FROM legacy_participant_duplicate d
                  WHERE d.participant_id = l.participant_id);

-- What is left has no derivable owner and no principal reference pointing at it.
DELETE
FROM external_participant
WHERE owner_organization_id IS NULL
  AND owner_app_user_id IS NULL;

ALTER TABLE external_participant
    ADD CONSTRAINT ck_external_participant_owner
        CHECK (
            (owner_organization_id IS NOT NULL AND owner_app_user_id IS NULL) OR
            (owner_organization_id IS NULL AND owner_app_user_id IS NOT NULL)
        );

CREATE UNIQUE INDEX uq_external_participant_owner_org_email
    ON external_participant (owner_organization_id, email_lower)
    WHERE owner_organization_id IS NOT NULL;

CREATE UNIQUE INDEX uq_external_participant_owner_user_email
    ON external_participant (owner_app_user_id, email_lower)
    WHERE owner_app_user_id IS NOT NULL;
