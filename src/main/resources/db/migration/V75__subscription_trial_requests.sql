CREATE TABLE subscription_trial_request
(
    id                       UUID          NOT NULL,
    owner_type               VARCHAR(32)   NOT NULL,
    owner_id                 UUID          NOT NULL,
    requested_by_app_user_id UUID          NOT NULL,
    plan_code                VARCHAR(32)   NOT NULL,
    status                   VARCHAR(32)   NOT NULL,
    request_note             VARCHAR(1024),
    requested_at             TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by_app_user_id  UUID,
    reviewed_at              TIMESTAMP(6),
    decision_reason          VARCHAR(1024),
    trial_grant_id           UUID,
    updated_at               TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_subscription_trial_request PRIMARY KEY (id),
    CONSTRAINT fk_subscription_trial_request_requester
        FOREIGN KEY (requested_by_app_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_subscription_trial_request_reviewer
        FOREIGN KEY (reviewed_by_app_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_subscription_trial_request_grant
        FOREIGN KEY (trial_grant_id) REFERENCES subscription_trial_grant (id),
    CONSTRAINT ck_subscription_trial_request_owner
        CHECK ((owner_type = 'USER' AND plan_code = 'PERSONAL')
            OR (owner_type = 'ORGANIZATION' AND plan_code = 'BUSINESS')),
    CONSTRAINT ck_subscription_trial_request_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_subscription_trial_request_note
        CHECK (request_note IS NULL OR BTRIM(request_note) <> ''),
    CONSTRAINT ck_subscription_trial_request_decision
        CHECK ((status = 'PENDING'
                AND reviewed_by_app_user_id IS NULL
                AND reviewed_at IS NULL
                AND decision_reason IS NULL
                AND trial_grant_id IS NULL)
            OR (status = 'APPROVED'
                AND reviewed_by_app_user_id IS NOT NULL
                AND reviewed_at IS NOT NULL
                AND decision_reason IS NOT NULL
                AND BTRIM(decision_reason) <> ''
                AND trial_grant_id IS NOT NULL)
            OR (status = 'REJECTED'
                AND reviewed_by_app_user_id IS NOT NULL
                AND reviewed_at IS NOT NULL
                AND decision_reason IS NOT NULL
                AND BTRIM(decision_reason) <> ''
                AND trial_grant_id IS NULL))
);

CREATE INDEX idx_subscription_trial_request_status
    ON subscription_trial_request (status, requested_at DESC);

CREATE INDEX idx_subscription_trial_request_owner
    ON subscription_trial_request (owner_type, owner_id, requested_at DESC);

CREATE UNIQUE INDEX uq_subscription_trial_request_pending_owner
    ON subscription_trial_request (owner_type, owner_id)
    WHERE status = 'PENDING';
