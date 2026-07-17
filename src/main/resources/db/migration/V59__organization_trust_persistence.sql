CREATE TABLE organization_trust_relationship (
    id UUID PRIMARY KEY,
    organization_a_id UUID NOT NULL,
    organization_b_id UUID NOT NULL,
    requested_by_organization_id UUID NOT NULL,
    requested_by_app_user_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_message VARCHAR(2000),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    request_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    activated_at TIMESTAMP WITH TIME ZONE,
    rejected_at TIMESTAMP WITH TIME ZONE,
    withdrawn_at TIMESTAMP WITH TIME ZONE,
    expired_at TIMESTAMP WITH TIME ZONE,
    ended_at TIMESTAMP WITH TIME ZONE,
    review_due_at TIMESTAMP WITH TIME ZONE,
    latest_transition_by_app_user_id UUID,
    latest_transition_reason VARCHAR(2000),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_org_trust_relationship_org_a
        FOREIGN KEY (organization_a_id) REFERENCES organization (id),
    CONSTRAINT fk_org_trust_relationship_org_b
        FOREIGN KEY (organization_b_id) REFERENCES organization (id),
    CONSTRAINT fk_org_trust_relationship_requesting_org
        FOREIGN KEY (requested_by_organization_id) REFERENCES organization (id),
    CONSTRAINT fk_org_trust_relationship_request_actor
        FOREIGN KEY (requested_by_app_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_org_trust_relationship_transition_actor
        FOREIGN KEY (latest_transition_by_app_user_id) REFERENCES app_user (id),
    CONSTRAINT ck_org_trust_relationship_canonical_organizations
        CHECK (organization_a_id < organization_b_id),
    CONSTRAINT ck_org_trust_relationship_requesting_party
        CHECK (requested_by_organization_id IN (organization_a_id, organization_b_id)),
    CONSTRAINT ck_org_trust_relationship_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED', 'WITHDRAWN', 'EXPIRED', 'ENDED')),
    CONSTRAINT ck_org_trust_relationship_request_expiry
        CHECK (request_expires_at > requested_at),
    CONSTRAINT ck_org_trust_relationship_lifecycle
        CHECK (
            (status = 'PENDING'
                AND activated_at IS NULL
                AND rejected_at IS NULL
                AND withdrawn_at IS NULL
                AND expired_at IS NULL
                AND ended_at IS NULL
                AND review_due_at IS NULL)
            OR
            (status = 'ACTIVE'
                AND activated_at IS NOT NULL
                AND rejected_at IS NULL
                AND withdrawn_at IS NULL
                AND expired_at IS NULL
                AND ended_at IS NULL
                AND review_due_at IS NOT NULL)
            OR
            (status = 'REJECTED'
                AND activated_at IS NULL
                AND rejected_at IS NOT NULL
                AND withdrawn_at IS NULL
                AND expired_at IS NULL
                AND ended_at IS NULL
                AND review_due_at IS NULL)
            OR
            (status = 'WITHDRAWN'
                AND activated_at IS NULL
                AND rejected_at IS NULL
                AND withdrawn_at IS NOT NULL
                AND expired_at IS NULL
                AND ended_at IS NULL
                AND review_due_at IS NULL)
            OR
            (status = 'EXPIRED'
                AND activated_at IS NULL
                AND rejected_at IS NULL
                AND withdrawn_at IS NULL
                AND expired_at IS NOT NULL
                AND ended_at IS NULL
                AND review_due_at IS NULL)
            OR
            (status = 'ENDED'
                AND activated_at IS NOT NULL
                AND rejected_at IS NULL
                AND withdrawn_at IS NULL
                AND expired_at IS NULL
                AND ended_at IS NOT NULL
                AND review_due_at IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uq_org_trust_relationship_current_organizations
    ON organization_trust_relationship (organization_a_id, organization_b_id)
    WHERE status IN ('PENDING', 'ACTIVE');

CREATE INDEX idx_org_trust_relationship_org_a_requested
    ON organization_trust_relationship (organization_a_id, requested_at DESC);

CREATE INDEX idx_org_trust_relationship_org_b_requested
    ON organization_trust_relationship (organization_b_id, requested_at DESC);

CREATE INDEX idx_org_trust_relationship_pending_expiry
    ON organization_trust_relationship (request_expires_at)
    WHERE status = 'PENDING';

CREATE TABLE organization_trust_suspension (
    id UUID PRIMARY KEY,
    relationship_id UUID NOT NULL,
    suspending_organization_id UUID NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    suspended_by_app_user_id UUID NOT NULL,
    suspended_at TIMESTAMP WITH TIME ZONE NOT NULL,
    cleared_by_app_user_id UUID,
    cleared_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_org_trust_suspension_relationship
        FOREIGN KEY (relationship_id) REFERENCES organization_trust_relationship (id),
    CONSTRAINT fk_org_trust_suspension_organization
        FOREIGN KEY (suspending_organization_id) REFERENCES organization (id),
    CONSTRAINT fk_org_trust_suspension_actor
        FOREIGN KEY (suspended_by_app_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_org_trust_suspension_clear_actor
        FOREIGN KEY (cleared_by_app_user_id) REFERENCES app_user (id),
    CONSTRAINT ck_org_trust_suspension_reason
        CHECK (btrim(reason) <> ''),
    CONSTRAINT ck_org_trust_suspension_clear
        CHECK (
            (cleared_by_app_user_id IS NULL AND cleared_at IS NULL)
            OR
            (cleared_by_app_user_id IS NOT NULL AND cleared_at IS NOT NULL AND cleared_at >= suspended_at)
        )
);

CREATE UNIQUE INDEX uq_org_trust_suspension_active_owner
    ON organization_trust_suspension (relationship_id, suspending_organization_id)
    WHERE cleared_at IS NULL;

CREATE INDEX idx_org_trust_suspension_relationship
    ON organization_trust_suspension (relationship_id, suspended_at DESC);

CREATE TABLE organization_trust_party_policy (
    id UUID PRIMARY KEY,
    relationship_id UUID NOT NULL,
    policy_owner_organization_id UUID NOT NULL,
    allow_exchanges_to_partner BOOLEAN NOT NULL DEFAULT FALSE,
    allow_exchanges_from_partner BOOLEAN NOT NULL DEFAULT FALSE,
    allow_partner_member_resolution BOOLEAN NOT NULL DEFAULT FALSE,
    allow_partner_group_discovery BOOLEAN NOT NULL DEFAULT FALSE,
    share_member_display_name BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP WITH TIME ZONE,
    review_due_at TIMESTAMP WITH TIME ZONE,
    revision BIGINT NOT NULL DEFAULT 0,
    updated_by_app_user_id UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_org_trust_party_policy_relationship
        FOREIGN KEY (relationship_id) REFERENCES organization_trust_relationship (id),
    CONSTRAINT fk_org_trust_party_policy_organization
        FOREIGN KEY (policy_owner_organization_id) REFERENCES organization (id),
    CONSTRAINT fk_org_trust_party_policy_actor
        FOREIGN KEY (updated_by_app_user_id) REFERENCES app_user (id),
    CONSTRAINT uq_org_trust_party_policy_owner
        UNIQUE (relationship_id, policy_owner_organization_id),
    CONSTRAINT ck_org_trust_party_policy_expiry
        CHECK (expires_at IS NULL OR expires_at > updated_at),
    CONSTRAINT ck_org_trust_party_policy_review
        CHECK (review_due_at IS NULL OR review_due_at > updated_at)
);

CREATE INDEX idx_org_trust_party_policy_owner
    ON organization_trust_party_policy (policy_owner_organization_id, relationship_id);

CREATE FUNCTION validate_org_trust_suspension_party()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    party_a UUID;
    party_b UUID;
BEGIN
    SELECT organization_a_id, organization_b_id
      INTO party_a, party_b
      FROM organization_trust_relationship
     WHERE id = NEW.relationship_id;

    IF NEW.suspending_organization_id NOT IN (party_a, party_b) THEN
        RAISE EXCEPTION 'Suspending organization must be a relationship party'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_org_trust_suspension_party
BEFORE INSERT OR UPDATE OF relationship_id, suspending_organization_id
ON organization_trust_suspension
FOR EACH ROW
EXECUTE FUNCTION validate_org_trust_suspension_party();

CREATE FUNCTION validate_org_trust_policy_party()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    party_a UUID;
    party_b UUID;
BEGIN
    SELECT organization_a_id, organization_b_id
      INTO party_a, party_b
      FROM organization_trust_relationship
     WHERE id = NEW.relationship_id;

    IF NEW.policy_owner_organization_id NOT IN (party_a, party_b) THEN
        RAISE EXCEPTION 'Policy owner organization must be a relationship party'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_org_trust_policy_party
BEFORE INSERT OR UPDATE OF relationship_id, policy_owner_organization_id
ON organization_trust_party_policy
FOR EACH ROW
EXECUTE FUNCTION validate_org_trust_policy_party();
