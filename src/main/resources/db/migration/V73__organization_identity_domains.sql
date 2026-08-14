CREATE TABLE organization_identity_domain (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    domain VARCHAR(253) NOT NULL,
    verification_token VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_date TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_date TIMESTAMP WITH TIME ZONE,
    created_by UUID,
    verified_by UUID,
    CONSTRAINT fk_org_identity_domain_organization
        FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_org_identity_domain_created_by
        FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT fk_org_identity_domain_verified_by
        FOREIGN KEY (verified_by) REFERENCES app_user (id),
    CONSTRAINT uq_org_identity_domain_organization_domain UNIQUE (organization_id, domain),
    CONSTRAINT ck_org_identity_domain_normalized
        CHECK (domain = LOWER(domain) AND domain = BTRIM(domain)),
    CONSTRAINT ck_org_identity_domain_status
        CHECK (status IN ('PENDING', 'VERIFIED')),
    CONSTRAINT ck_org_identity_domain_verification_date
        CHECK (
            (status = 'PENDING' AND verified_date IS NULL AND verified_by IS NULL)
            OR
            (status = 'VERIFIED' AND verified_date IS NOT NULL AND verified_by IS NOT NULL)
        )
);

CREATE INDEX idx_org_identity_domain_organization
    ON organization_identity_domain (organization_id, created_date);

CREATE UNIQUE INDEX uq_org_identity_domain_verified_domain
    ON organization_identity_domain (domain)
    WHERE status = 'VERIFIED';
