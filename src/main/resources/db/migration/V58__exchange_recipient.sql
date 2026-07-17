CREATE TABLE exchange_recipient (
    id UUID PRIMARY KEY,
    exchange_id UUID NOT NULL,
    direct_share_id UUID NOT NULL UNIQUE,
    purpose VARCHAR(32) NOT NULL,
    selection_type VARCHAR(32) NOT NULL,
    target_organization_id UUID,
    acceptance_status VARCHAR(32) NOT NULL,
    accepted_or_rejected_by_app_user_id UUID,
    accepted_or_rejected_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_exchange_recipient_exchange
        FOREIGN KEY (exchange_id) REFERENCES exchange (id) ON DELETE CASCADE,
    CONSTRAINT fk_exchange_recipient_share
        FOREIGN KEY (direct_share_id) REFERENCES share (id) ON DELETE CASCADE,
    CONSTRAINT fk_exchange_recipient_target_organization
        FOREIGN KEY (target_organization_id) REFERENCES organization (id),
    CONSTRAINT fk_exchange_recipient_decision_actor
        FOREIGN KEY (accepted_or_rejected_by_app_user_id) REFERENCES app_user (id),
    CONSTRAINT ck_exchange_recipient_purpose
        CHECK (purpose IN ('PRIMARY', 'PARTICIPANT')),
    CONSTRAINT ck_exchange_recipient_selection_type
        CHECK (selection_type IN (
            'REGISTERED_USER',
            'EXTERNAL_EMAIL',
            'INTERNAL_GROUP',
            'PERSONAL_GROUP',
            'TRUSTED_PERSON',
            'TRUSTED_GROUP'
        )),
    CONSTRAINT ck_exchange_recipient_acceptance_status
        CHECK (acceptance_status IN ('NOT_REQUIRED', 'PENDING', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT ck_exchange_recipient_participant_acceptance
        CHECK (purpose = 'PRIMARY' OR acceptance_status = 'NOT_REQUIRED'),
    CONSTRAINT ck_exchange_recipient_decision
        CHECK (
            (acceptance_status IN ('NOT_REQUIRED', 'PENDING')
                AND accepted_or_rejected_by_app_user_id IS NULL
                AND accepted_or_rejected_at IS NULL)
            OR
            (acceptance_status IN ('ACCEPTED', 'REJECTED')
                AND accepted_or_rejected_by_app_user_id IS NOT NULL
                AND accepted_or_rejected_at IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uq_exchange_recipient_primary
    ON exchange_recipient (exchange_id)
    WHERE purpose = 'PRIMARY';

CREATE INDEX idx_exchange_recipient_exchange
    ON exchange_recipient (exchange_id);

CREATE INDEX idx_exchange_recipient_target_organization
    ON exchange_recipient (target_organization_id);
