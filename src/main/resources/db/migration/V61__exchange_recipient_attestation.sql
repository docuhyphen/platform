CREATE TABLE exchange_recipient_attestation (
    id UUID PRIMARY KEY,
    exchange_recipient_id UUID NOT NULL UNIQUE,
    relationship_id UUID NOT NULL,
    caller_organization_id UUID NOT NULL,
    target_organization_id UUID NOT NULL,
    sender_policy_revision BIGINT NOT NULL,
    target_policy_revision BIGINT NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_app_user_id UUID,
    subject_membership_id UUID,
    subject_group_id UUID,
    invited_email_snapshot VARCHAR(320),
    display_name_snapshot VARCHAR(512),
    organization_name_snapshot VARCHAR(512) NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verification_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    acceptance_verified_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_exchange_recipient_attestation_recipient
        FOREIGN KEY (exchange_recipient_id) REFERENCES exchange_recipient (id) ON DELETE CASCADE,
    CONSTRAINT fk_exchange_recipient_attestation_relationship
        FOREIGN KEY (relationship_id) REFERENCES organization_trust_relationship (id),
    CONSTRAINT fk_exchange_recipient_attestation_caller_organization
        FOREIGN KEY (caller_organization_id) REFERENCES organization (id),
    CONSTRAINT fk_exchange_recipient_attestation_target_organization
        FOREIGN KEY (target_organization_id) REFERENCES organization (id),
    CONSTRAINT fk_exchange_recipient_attestation_subject_user
        FOREIGN KEY (subject_app_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_exchange_recipient_attestation_subject_membership
        FOREIGN KEY (subject_membership_id) REFERENCES organization_membership (id),
    CONSTRAINT fk_exchange_recipient_attestation_subject_group
        FOREIGN KEY (subject_group_id) REFERENCES principal_group (id),
    CONSTRAINT ck_exchange_recipient_attestation_distinct_organizations
        CHECK (caller_organization_id <> target_organization_id),
    CONSTRAINT ck_exchange_recipient_attestation_subject_type
        CHECK (subject_type IN ('PERSON', 'GROUP')),
    CONSTRAINT ck_exchange_recipient_attestation_subject
        CHECK (
            (subject_type = 'PERSON'
                AND subject_app_user_id IS NOT NULL
                AND subject_membership_id IS NOT NULL
                AND subject_group_id IS NULL)
            OR
            (subject_type = 'GROUP'
                AND subject_app_user_id IS NULL
                AND subject_membership_id IS NULL
                AND subject_group_id IS NOT NULL
                AND invited_email_snapshot IS NULL)
        ),
    CONSTRAINT ck_exchange_recipient_attestation_expiry
        CHECK (verification_expires_at > verified_at),
    CONSTRAINT ck_exchange_recipient_attestation_acceptance_time
        CHECK (acceptance_verified_at IS NULL OR acceptance_verified_at >= verified_at)
);

CREATE INDEX idx_exchange_recipient_attestation_relationship
    ON exchange_recipient_attestation (relationship_id);

CREATE INDEX idx_exchange_recipient_attestation_group
    ON exchange_recipient_attestation (subject_group_id)
    WHERE subject_group_id IS NOT NULL;

CREATE FUNCTION validate_exchange_recipient_attestation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    recipient_target UUID;
    recipient_selection VARCHAR(32);
    relationship_a UUID;
    relationship_b UUID;
BEGIN
    SELECT target_organization_id, selection_type
      INTO recipient_target, recipient_selection
      FROM exchange_recipient
     WHERE id = NEW.exchange_recipient_id;

    SELECT organization_a_id, organization_b_id
      INTO relationship_a, relationship_b
      FROM organization_trust_relationship
     WHERE id = NEW.relationship_id;

    IF NEW.caller_organization_id NOT IN (relationship_a, relationship_b)
        OR NEW.target_organization_id NOT IN (relationship_a, relationship_b)
        OR NEW.caller_organization_id = NEW.target_organization_id THEN
        RAISE EXCEPTION 'Attestation organizations must be the relationship parties'
            USING ERRCODE = '23514';
    END IF;

    IF recipient_target IS DISTINCT FROM NEW.target_organization_id THEN
        RAISE EXCEPTION 'Attestation target must match the recipient target organization'
            USING ERRCODE = '23514';
    END IF;

    IF (NEW.subject_type = 'GROUP' AND recipient_selection <> 'TRUSTED_GROUP')
        OR (NEW.subject_type = 'PERSON' AND recipient_selection <> 'TRUSTED_PERSON') THEN
        RAISE EXCEPTION 'Attestation subject must match the recipient selection type'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_exchange_recipient_attestation
BEFORE INSERT OR UPDATE OF exchange_recipient_id, relationship_id,
    caller_organization_id, target_organization_id, subject_type
ON exchange_recipient_attestation
FOR EACH ROW
EXECUTE FUNCTION validate_exchange_recipient_attestation();
