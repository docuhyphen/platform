-- Subscription ownership model.
--
-- Free and Personal are individual plans owned by a registered app user. Business is the only
-- organization plan and is priced per purchased seat. No-account recipients are not subscribers,
-- so they receive no row here and never consume seat capacity.

CREATE TABLE user_subscription_policy
(
    id                                UUID          NOT NULL,
    app_user_id                       UUID          NOT NULL,
    plan_code                         VARCHAR(32)   NOT NULL,
    subscription_status               VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
    billing_frequency                 VARCHAR(16),
    current_period_start              TIMESTAMP(6),
    current_period_end                TIMESTAMP(6),
    grace_period_end                  TIMESTAMP(6),
    external_billing_customer_ref     VARCHAR(255),
    external_billing_subscription_ref VARCHAR(255),
    change_reason                     VARCHAR(1024),
    created_date                      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_date                      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_subscription_policy PRIMARY KEY (id),
    CONSTRAINT fk_user_subscription_policy_app_user
        FOREIGN KEY (app_user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_subscription_policy_app_user
        UNIQUE (app_user_id),
    -- Business is an organization plan and must never be assigned to an individual account.
    CONSTRAINT ck_user_subscription_policy_plan_code
        CHECK (plan_code IN ('FREE', 'PERSONAL')),
    CONSTRAINT ck_user_subscription_policy_status
        CHECK (subscription_status IN ('TRIALING', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELED')),
    CONSTRAINT ck_user_subscription_policy_billing_frequency
        CHECK (billing_frequency IS NULL OR billing_frequency IN ('MONTHLY', 'ANNUAL')),
    CONSTRAINT ck_user_subscription_policy_period_order
        CHECK (current_period_start IS NULL
                   OR current_period_end IS NULL
                   OR current_period_end >= current_period_start)
);

CREATE INDEX idx_user_subscription_policy_plan_code
    ON user_subscription_policy (plan_code);

CREATE INDEX idx_user_subscription_policy_status
    ON user_subscription_policy (subscription_status);

-- Accounts that already exist keep the feature set they have been using. They are grandfathered
-- onto Personal so that introducing enforcement never removes access from a working account.
-- Temporary recipient placeholders and machine accounts are excluded because neither is a
-- registered individual subscriber.
INSERT INTO user_subscription_policy (id,
                                      app_user_id,
                                      plan_code,
                                      subscription_status,
                                      change_reason,
                                      created_date,
                                      updated_date)
SELECT gen_random_uuid(),
       u.id,
       'PERSONAL',
       'ACTIVE',
       'Existing account grandfathered onto the Personal plan when subscription records were introduced',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM app_user u
WHERE u.is_temporary = FALSE
  AND u.application_id IS NULL
  AND NOT EXISTS (SELECT 1
                  FROM user_subscription_policy p
                  WHERE p.app_user_id = u.id);


-- Organization policies gain the same billing lifecycle fields as individual policies. Columns
-- are added nullable, populated, and only then constrained so the migration never fails on
-- existing rows.
ALTER TABLE organization_subscription_policy
    ADD COLUMN subscription_status               VARCHAR(32),
    ADD COLUMN billing_frequency                 VARCHAR(16),
    ADD COLUMN current_period_start              TIMESTAMP(6),
    ADD COLUMN current_period_end                TIMESTAMP(6),
    ADD COLUMN grace_period_end                  TIMESTAMP(6),
    ADD COLUMN external_billing_customer_ref     VARCHAR(255),
    ADD COLUMN external_billing_subscription_ref VARCHAR(255);

UPDATE organization_subscription_policy
SET subscription_status = 'ACTIVE'
WHERE subscription_status IS NULL;

-- Organizations are Business subscribers. Existing purchased seat capacity in max_users is kept
-- exactly as it was so no organization loses capacity through this change.
UPDATE organization_subscription_policy
SET tier_code     = 'BUSINESS',
    change_reason = COALESCE(change_reason, 'Organization migrated to the Business plan'),
    updated_date  = CURRENT_TIMESTAMP
WHERE tier_code <> 'BUSINESS';

-- Collapse any historical duplicates so one organization owns exactly one policy row, keeping
-- the most recently updated record.
DELETE
FROM organization_subscription_policy p
WHERE EXISTS (SELECT 1
              FROM organization_subscription_policy newer
              WHERE newer.organization_id = p.organization_id
                AND (newer.updated_date, newer.id) > (p.updated_date, p.id));

-- Organizations previously relying on an implicit default now get an explicit Business record.
-- Seat capacity is left unset so a platform administrator assigns purchased seats deliberately
-- rather than an implicit cap silently locking members out.
INSERT INTO organization_subscription_policy (id,
                                              organization_id,
                                              tier_code,
                                              max_users,
                                              subscription_status,
                                              change_reason,
                                              created_date,
                                              updated_date)
SELECT gen_random_uuid(),
       o.id,
       'BUSINESS',
       NULL,
       'ACTIVE',
       'Business subscription created for an organization that had no explicit policy',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM organization o
WHERE NOT EXISTS (SELECT 1
                  FROM organization_subscription_policy p
                  WHERE p.organization_id = o.id);

ALTER TABLE organization_subscription_policy
    ALTER COLUMN subscription_status SET DEFAULT 'ACTIVE';

ALTER TABLE organization_subscription_policy
    ALTER COLUMN subscription_status SET NOT NULL;

ALTER TABLE organization_subscription_policy
    ADD CONSTRAINT uq_organization_subscription_policy_organization
        UNIQUE (organization_id),
    -- Free and Personal are individual plans and must never be assigned to an organization.
    ADD CONSTRAINT ck_organization_subscription_policy_tier_code
        CHECK (tier_code = 'BUSINESS'),
    ADD CONSTRAINT ck_organization_subscription_policy_status
        CHECK (subscription_status IN ('TRIALING', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELED')),
    ADD CONSTRAINT ck_organization_subscription_policy_billing_frequency
        CHECK (billing_frequency IS NULL OR billing_frequency IN ('MONTHLY', 'ANNUAL')),
    ADD CONSTRAINT ck_organization_subscription_policy_max_users
        CHECK (max_users IS NULL OR max_users > 0),
    ADD CONSTRAINT ck_organization_subscription_policy_period_order
        CHECK (current_period_start IS NULL
                   OR current_period_end IS NULL
                   OR current_period_end >= current_period_start);

CREATE INDEX idx_organization_subscription_policy_status
    ON organization_subscription_policy (subscription_status);

