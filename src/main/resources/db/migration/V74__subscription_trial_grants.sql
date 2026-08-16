CREATE TABLE subscription_trial_grant
(
    id                     UUID          NOT NULL,
    owner_type             VARCHAR(32)   NOT NULL,
    owner_id               UUID          NOT NULL,
    plan_code              VARCHAR(32)   NOT NULL,
    started_at             TIMESTAMP(6)  NOT NULL,
    ended_at               TIMESTAMP(6)  NOT NULL,
    source                 VARCHAR(32)   NOT NULL,
    granted_by_app_user_id UUID,
    reason                 VARCHAR(1024) NOT NULL,
    created_at             TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_subscription_trial_grant PRIMARY KEY (id),
    CONSTRAINT fk_subscription_trial_grant_actor
        FOREIGN KEY (granted_by_app_user_id) REFERENCES app_user (id),
    CONSTRAINT ck_subscription_trial_grant_owner
        CHECK ((owner_type = 'USER' AND plan_code = 'PERSONAL')
            OR (owner_type = 'ORGANIZATION' AND plan_code = 'BUSINESS')),
    CONSTRAINT ck_subscription_trial_grant_period
        CHECK (ended_at > started_at),
    CONSTRAINT ck_subscription_trial_grant_source
        CHECK (source IN ('AUTOMATIC', 'PLATFORM_ADMIN')),
    CONSTRAINT ck_subscription_trial_grant_actor
        CHECK (source <> 'PLATFORM_ADMIN' OR granted_by_app_user_id IS NOT NULL),
    CONSTRAINT ck_subscription_trial_grant_reason
        CHECK (BTRIM(reason) <> '')
);

CREATE INDEX idx_subscription_trial_grant_owner
    ON subscription_trial_grant (owner_type, owner_id, created_at DESC);

CREATE UNIQUE INDEX uq_subscription_trial_grant_automatic_owner
    ON subscription_trial_grant (owner_type, owner_id)
    WHERE source = 'AUTOMATIC';
