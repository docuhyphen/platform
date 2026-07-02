-- Workflow Webhook Endpoint
--
-- Stores outbound webhook registrations for the workflow engine. Signing credentials
-- are kept in signing_secret_hash (BCrypt) and are completely separate from the
-- inbound application api_secret_hash column in the application table.
-- target_url is validated against WebhookDestinationPolicy (SSRF guard) on write.

CREATE TABLE workflow_webhook_endpoint (
    id                       UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    owner_organization_id    UUID         NOT NULL REFERENCES organization(id),
    workflow_definition_id   UUID         NOT NULL,
    registered_application_id UUID,
    target_url               VARCHAR(2048) NOT NULL,
    signing_secret_hash      VARCHAR(256) NOT NULL,
    signing_secret_version   INTEGER      NOT NULL DEFAULT 1,
    is_enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    permitted_event_types    VARCHAR(1024) NOT NULL DEFAULT '[]',
    created_date             TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_date             TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_webhook_target_url_not_blank CHECK (length(trim(target_url)) > 0),
    CONSTRAINT chk_webhook_signing_version_positive CHECK (signing_secret_version >= 1)
);

CREATE INDEX idx_webhook_endpoint_org ON workflow_webhook_endpoint (owner_organization_id);
CREATE INDEX idx_webhook_endpoint_workflow ON workflow_webhook_endpoint (workflow_definition_id);
