-- Who created a stored document version, named as the canonical principal.
--
-- The released schema records a creator as a foreign key into app_user plus a free-text email. An
-- external participant, a link-verified recipient, a registered application, and a service
-- principal all reach the same version-creation path, and a version created by any of them today
-- either names the wrong table or carries an email nobody can resolve. The canonical (kind, id)
-- pair names every one of them without introducing a second identity model.
--
-- Expand-contract: the canonical columns are added and filled from the trustworthy legacy key, the
-- legacy key stays and keeps its meaning for registered users, and a consistency rule stops the two
-- from drifting apart while both exist. A writer that predates this release states the legacy key
-- alone, which stays valid until those writers are drained and the old column is contracted.

ALTER TABLE document_version
    ADD COLUMN created_by_principal_kind VARCHAR(32),
    ADD COLUMN created_by_principal_id   uuid;

-- The foreign key is the only creator statement this schema can be trusted on, and a registered
-- user is the only thing it can mean. A version that names only an email keeps that email as its
-- history label rather than gaining a principal resolved from an address.
UPDATE document_version
SET created_by_principal_kind = 'USER',
    created_by_principal_id   = created_by
WHERE created_by IS NOT NULL;

-- Rows whose only creator statement is an email are reported rather than guessed at, so an
-- operator can see how much history stays without a canonical principal.
DO
$$
    DECLARE
        unresolvable_creators bigint;
    BEGIN
        SELECT count(*)
        INTO unresolvable_creators
        FROM document_version
        WHERE created_by IS NULL
          AND btrim(coalesce(createdbyemail, '')) <> '';

        IF unresolvable_creators > 0 THEN
            RAISE NOTICE
                'document_version: % row(s) name a creator by email only and keep no canonical principal',
                unresolvable_creators;
        END IF;
    END
$$;

ALTER TABLE document_version
    ADD CONSTRAINT ck_document_version_creator_principal_kind CHECK (
        created_by_principal_kind IS NULL
            OR created_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                             'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        ),
    -- Half a principal identifies nobody, so a kind and an ID only ever appear together.
    ADD CONSTRAINT ck_document_version_creator_principal_pair CHECK (
        (created_by_principal_kind IS NULL) = (created_by_principal_id IS NULL)
        ),
    -- While both shapes exist they may only ever name the same registered user, so contracting the
    -- legacy key later cannot change what any row says. A row that states the legacy key alone is
    -- still accepted, because a writer that predates this release keeps serving during a rolling
    -- deployment.
    ADD CONSTRAINT ck_document_version_creator_principal_legacy CHECK (
        created_by IS NULL
            OR created_by_principal_kind IS NULL
            OR (created_by_principal_kind = 'USER' AND created_by_principal_id = created_by)
        );

CREATE INDEX ix_document_version_created_by_principal
    ON document_version (created_by_principal_kind, created_by_principal_id);
