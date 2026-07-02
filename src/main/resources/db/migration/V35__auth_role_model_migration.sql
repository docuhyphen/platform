-- Authorization role model migration.
-- Moves from the legacy flat role_assignment table (with scope_type discrimination)
-- to three purpose-built tables: app_role_assignment (APP scope), and
-- organization_membership_role (ORG scope, replaces the single role_name column on
-- organization_membership). The resource and principal_group scope slices of
-- role_assignment were already superseded by Share and PrincipalGroupMember respectively
-- and contain no data worth preserving.
--
-- The V1 baseline already contains the new schema for fresh installs.
-- This migration handles the upgrade path for existing databases.
-- All blocks are idempotent: safe to run against both old and new baseline databases.

DO $$
BEGIN

-- ── 1. app_role_assignment ────────────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM pg_tables WHERE tablename = 'app_role_assignment') THEN

    CREATE TABLE app_role_assignment (
        id                      UUID         NOT NULL,
        app_user_id             UUID         NOT NULL,
        role_name               VARCHAR(64)  NOT NULL,
        granted_by_app_user_id  UUID,
        granted_at              TIMESTAMP(6) NOT NULL DEFAULT NOW(),
        expires_at              TIMESTAMP(6),
        is_active               BOOLEAN      NOT NULL DEFAULT true,
        CONSTRAINT app_role_assignment_pkey            PRIMARY KEY (id),
        CONSTRAINT uq_app_role_assignment_user_role    UNIQUE (app_user_id, role_name),
        CONSTRAINT app_role_assignment_role_name_check CHECK (
            role_name IN ('APP_ADMIN', 'APP_AUDITOR', 'APP_SUPPORT', 'APP_USER')
        )
    );

    ALTER TABLE app_role_assignment
        ADD CONSTRAINT fk_app_role_assignment_user
            FOREIGN KEY (app_user_id) REFERENCES app_user(id);
    ALTER TABLE app_role_assignment
        ADD CONSTRAINT fk_app_role_assignment_grantor
            FOREIGN KEY (granted_by_app_user_id) REFERENCES app_user(id);

    CREATE INDEX ix_app_role_assignment_active
        ON app_role_assignment (app_user_id, role_name) WHERE (is_active = true);

    -- Migrate APP-scope rows from role_assignment (only present on old-baseline databases).
    IF EXISTS (SELECT 1 FROM pg_tables WHERE tablename = 'role_assignment') THEN
        INSERT INTO app_role_assignment
            (id, app_user_id, role_name, granted_by_app_user_id, granted_at, expires_at, is_active)
        SELECT
            id,
            app_user_id,
            CASE role_name WHEN 'END_USER' THEN 'APP_USER' ELSE role_name END,
            granted_by_app_user_id,
            granted_at,
            expires_at,
            is_active
        FROM role_assignment
        WHERE scope_type = 'APP'
          AND app_user_id IS NOT NULL
          AND role_name IN ('APP_ADMIN', 'APP_AUDITOR', 'APP_SUPPORT', 'END_USER')
        ON CONFLICT (app_user_id, role_name) DO NOTHING;
    END IF;

END IF;


-- ── 2. organization_membership_role ──────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM pg_tables WHERE tablename = 'organization_membership_role') THEN

    CREATE TABLE organization_membership_role (
        organization_membership_id  UUID        NOT NULL,
        role_name                   VARCHAR(64) NOT NULL,
        CONSTRAINT organization_membership_role_pkey PRIMARY KEY (organization_membership_id, role_name),
        CONSTRAINT organization_membership_role_name_check CHECK (
            role_name IN (
                'ORG_OWNER', 'ORG_ADMIN', 'ORG_BILLING_ADMIN', 'ORG_USER_MANAGER',
                'ORG_AUDITOR', 'ORG_MEMBER', 'ORG_GUEST'
            )
        )
    );

    ALTER TABLE organization_membership_role
        ADD CONSTRAINT fk_org_membership_role_membership
            FOREIGN KEY (organization_membership_id)
            REFERENCES organization_membership(id) ON DELETE CASCADE;

END IF;

-- Migrate role_name only when the column still exists on organization_membership.
IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'organization_membership' AND column_name = 'role_name'
) THEN

    INSERT INTO organization_membership_role (organization_membership_id, role_name)
    SELECT
        id,
        CASE role_name
            WHEN 'OWNER'  THEN 'ORG_OWNER'
            WHEN 'MEMBER' THEN 'ORG_MEMBER'
            ELSE role_name
        END
    FROM organization_membership
    WHERE role_name IN (
        'ORG_OWNER', 'ORG_ADMIN', 'ORG_BILLING_ADMIN', 'ORG_USER_MANAGER',
        'ORG_AUDITOR', 'ORG_MEMBER', 'ORG_GUEST', 'OWNER', 'MEMBER'
    )
    ON CONFLICT DO NOTHING;

    -- Any membership still without a role gets ORG_MEMBER.
    INSERT INTO organization_membership_role (organization_membership_id, role_name)
    SELECT m.id, 'ORG_MEMBER'
    FROM organization_membership m
    WHERE NOT EXISTS (
        SELECT 1 FROM organization_membership_role r
        WHERE r.organization_membership_id = m.id
    )
    ON CONFLICT DO NOTHING;

    ALTER TABLE organization_membership DROP COLUMN role_name;

END IF;


-- ── 3. application.role_name ─────────────────────────────────────────────────

IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'application' AND column_name = 'role_name'
) THEN
    ALTER TABLE application
        ADD COLUMN role_name VARCHAR(32) NOT NULL DEFAULT 'APPLICATION';

    ALTER TABLE application
        ADD CONSTRAINT application_role_name_check CHECK (role_name = 'APPLICATION');
END IF;


-- ── 4. Drop legacy role_assignment table ──────────────────────────────────────

IF EXISTS (SELECT 1 FROM pg_tables WHERE tablename = 'role_assignment') THEN
    DROP INDEX IF EXISTS ix_role_assignment_scope;
    DROP INDEX IF EXISTS ix_role_assignment_svc_scope;
    DROP INDEX IF EXISTS ix_role_assignment_user_scope;
    DROP TABLE role_assignment;
END IF;

END $$;
