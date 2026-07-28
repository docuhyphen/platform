CREATE TABLE organization_feature_entitlement
(
    id                     UUID         NOT NULL,
    organization_id        UUID         NOT NULL,
    feature_code           VARCHAR(64)  NOT NULL,
    is_enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_by_app_user_id UUID         NOT NULL,
    created_date           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_date           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_organization_feature_entitlement PRIMARY KEY (id),
    CONSTRAINT fk_organization_feature_entitlement_organization
        FOREIGN KEY (organization_id) REFERENCES organization (id) ON DELETE CASCADE,
    CONSTRAINT fk_organization_feature_entitlement_updated_by
        FOREIGN KEY (updated_by_app_user_id) REFERENCES app_user (id),
    CONSTRAINT uq_organization_feature_entitlement_code
        UNIQUE (organization_id, feature_code),
    CONSTRAINT ck_organization_feature_entitlement_code
        CHECK (feature_code ~ '^[A-Z][A-Z0-9_]{0,63}$')
);

CREATE INDEX idx_organization_feature_entitlement_organization
    ON organization_feature_entitlement (organization_id);
