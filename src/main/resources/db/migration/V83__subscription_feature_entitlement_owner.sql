-- A platform-administered feature decision can name a person as its owner.
--
-- The override table recorded one owner only, an organization, so whatever an individual account's
-- plan omitted was unreachable: nobody could add a feature to it and nobody could withdraw one. The
-- table now names the kind of owner and carries an id for each kind, which is the same shape the
-- rest of the platform already uses for an owner that may be an organization or a person.
--
-- Uniqueness moves with it. The released constraint keyed a decision by (organization_id,
-- feature_code), and an organization id that is now nullable stops constraining anything: two rows
-- for one person would both hold NULL there and neither would be refused. The replacement keys the
-- decision by the owner that made it, with the owner kind leading so an organization and a person
-- whose ids happen to be equal keep separate key spaces.
--
-- Every decision already recorded belongs to an organization and stays exactly as it was.

ALTER TABLE organization_feature_entitlement
    RENAME TO subscription_feature_entitlement;

ALTER TABLE subscription_feature_entitlement
    RENAME CONSTRAINT pk_organization_feature_entitlement TO pk_subscription_feature_entitlement;
ALTER TABLE subscription_feature_entitlement
    RENAME CONSTRAINT fk_organization_feature_entitlement_organization
        TO fk_subscription_feature_entitlement_organization;
ALTER TABLE subscription_feature_entitlement
    RENAME CONSTRAINT fk_organization_feature_entitlement_updated_by
        TO fk_subscription_feature_entitlement_updated_by;
ALTER TABLE subscription_feature_entitlement
    RENAME CONSTRAINT ck_organization_feature_entitlement_code
        TO ck_subscription_feature_entitlement_code;

ALTER INDEX idx_organization_feature_entitlement_organization
    RENAME TO ix_subscription_feature_entitlement_organization;

-- ── Owners ───────────────────────────────────────────────────────────────────────────────────

ALTER TABLE subscription_feature_entitlement
    ADD COLUMN owner_type  varchar(32),
    ADD COLUMN app_user_id uuid;

ALTER TABLE subscription_feature_entitlement
    ADD CONSTRAINT fk_subscription_feature_entitlement_app_user
        FOREIGN KEY (app_user_id) REFERENCES app_user (id) ON DELETE CASCADE;

-- Everything the released table holds was recorded against an organization, which is the only
-- owner it could express.
UPDATE subscription_feature_entitlement
SET owner_type = 'ORGANIZATION';

ALTER TABLE subscription_feature_entitlement
    ALTER COLUMN owner_type SET NOT NULL;
ALTER TABLE subscription_feature_entitlement
    ALTER COLUMN organization_id DROP NOT NULL;

-- An owner kind names exactly one owner, and no other kind of owner exists. Two owners on one row,
-- or none, leaves no answer to whose commercial position the decision changes.
ALTER TABLE subscription_feature_entitlement
    ADD CONSTRAINT ck_subscription_feature_entitlement_owner CHECK (
        (owner_type = 'ORGANIZATION' AND organization_id IS NOT NULL AND app_user_id IS NULL) OR
        (owner_type = 'USER' AND organization_id IS NULL AND app_user_id IS NOT NULL)
        );

-- ── One decision per feature per owner ───────────────────────────────────────────────────────

ALTER TABLE subscription_feature_entitlement
    DROP CONSTRAINT uq_organization_feature_entitlement_code;

CREATE UNIQUE INDEX ux_subscription_feature_entitlement_owner_code
    ON subscription_feature_entitlement (
                                         owner_type,
                                         COALESCE(organization_id, app_user_id),
                                         feature_code
        );

CREATE INDEX ix_subscription_feature_entitlement_app_user
    ON subscription_feature_entitlement (app_user_id);
