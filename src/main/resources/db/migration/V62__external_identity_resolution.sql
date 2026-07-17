CREATE TABLE external_identity_resolution (
    id UUID PRIMARY KEY,
    actor_app_user_id UUID NOT NULL,
    caller_organization_id UUID NOT NULL,
    target_organization_id UUID NOT NULL,
    relationship_id UUID NOT NULL,
    sender_policy_revision BIGINT NOT NULL,
    target_policy_revision BIGINT NOT NULL,
    resolved_app_user_id UUID NOT NULL,
    resolved_membership_id UUID NOT NULL,
    normalized_email VARCHAR(320) NOT NULL,
    display_name_snapshot VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    consumed_by_exchange_id UUID,
    CONSTRAINT fk_external_identity_resolution_actor
        FOREIGN KEY (actor_app_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_external_identity_resolution_caller_organization
        FOREIGN KEY (caller_organization_id) REFERENCES organization (id),
    CONSTRAINT fk_external_identity_resolution_target_organization
        FOREIGN KEY (target_organization_id) REFERENCES organization (id),
    CONSTRAINT fk_external_identity_resolution_relationship
        FOREIGN KEY (relationship_id) REFERENCES organization_trust_relationship (id),
    CONSTRAINT fk_external_identity_resolution_user
        FOREIGN KEY (resolved_app_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_external_identity_resolution_membership
        FOREIGN KEY (resolved_membership_id) REFERENCES organization_membership (id),
    CONSTRAINT fk_external_identity_resolution_exchange
        FOREIGN KEY (consumed_by_exchange_id) REFERENCES exchange (id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_external_identity_resolution_distinct_organizations
        CHECK (caller_organization_id <> target_organization_id),
    CONSTRAINT ck_external_identity_resolution_revisions
        CHECK (sender_policy_revision >= 0 AND target_policy_revision >= 0),
    CONSTRAINT ck_external_identity_resolution_expiry
        CHECK (expires_at > created_at),
    CONSTRAINT ck_external_identity_resolution_consumption
        CHECK (
            (consumed_at IS NULL AND consumed_by_exchange_id IS NULL)
            OR
            (consumed_at IS NOT NULL AND consumed_by_exchange_id IS NOT NULL AND consumed_at >= created_at)
        )
);

CREATE INDEX idx_external_identity_resolution_expiry
    ON external_identity_resolution (expires_at);

CREATE INDEX idx_external_identity_resolution_actor
    ON external_identity_resolution (actor_app_user_id, caller_organization_id);

CREATE FUNCTION validate_external_identity_resolution()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    relationship_a UUID;
    relationship_b UUID;
    membership_user UUID;
    membership_organization UUID;
BEGIN
    SELECT organization_a_id, organization_b_id
      INTO relationship_a, relationship_b
      FROM organization_trust_relationship
     WHERE id = NEW.relationship_id;

    SELECT app_user_id, organization_id
      INTO membership_user, membership_organization
      FROM organization_membership
     WHERE id = NEW.resolved_membership_id;

    IF NEW.caller_organization_id NOT IN (relationship_a, relationship_b)
        OR NEW.target_organization_id NOT IN (relationship_a, relationship_b)
        OR NEW.caller_organization_id = NEW.target_organization_id THEN
        RAISE EXCEPTION 'Resolution organizations must be the relationship parties'
            USING ERRCODE = '23514';
    END IF;

    IF membership_user IS DISTINCT FROM NEW.resolved_app_user_id
        OR membership_organization IS DISTINCT FROM NEW.target_organization_id THEN
        RAISE EXCEPTION 'Resolution membership must match the resolved user and target organization'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_external_identity_resolution
BEFORE INSERT OR UPDATE OF relationship_id, caller_organization_id, target_organization_id,
    resolved_app_user_id, resolved_membership_id
ON external_identity_resolution
FOR EACH ROW
EXECUTE FUNCTION validate_external_identity_resolution();
