--
-- V1 - Consolidated baseline schema (Exchange-native).
--
-- Single from-scratch schema for the app. "Exchange" is the first-class concept here.
-- Seed data lives in V2__seed.sql.
--

--
-- Name: access_audit_log; Type: TABLE
--

CREATE TABLE access_audit_log (
    id uuid NOT NULL,
    created_date TIMESTAMP(6) NOT NULL,
    action VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    actor_kind VARCHAR(32) NOT NULL,
    actor_id uuid,
    actor_email VARCHAR(255),
    target_resource_type VARCHAR(32),
    target_resource_id uuid,
    target_principal_kind VARCHAR(32),
    target_principal_id uuid,
    organization_id uuid,
    reason_code VARCHAR(64),
    reason VARCHAR(2048),
    before_snapshot text,
    after_snapshot text,
    event_hash VARCHAR(128) NOT NULL,
    prev_event_hash VARCHAR(128),
    CONSTRAINT access_audit_log_outcome_check CHECK ((outcome IN ('ALLOW', 'DENY', 'GRANT', 'REVOKE', 'TRANSITION')))
);


--
-- Name: app_user; Type: TABLE
--

CREATE TABLE app_user (
    email_verification_completed boolean,
    is_active boolean NOT NULL,
    is_temporary boolean NOT NULL,
    sign_in_attempts integer NOT NULL,
    created_date TIMESTAMP(6) NOT NULL,
    deprovisioned_at TIMESTAMP(6),
    exchange_version bigint NOT NULL,
    application_id uuid,
    id uuid NOT NULL,
    organization_id uuid,
    person_id uuid,
    settings_id uuid,
    email VARCHAR(255) NOT NULL,
    multifactor_authentication_type VARCHAR(255) NOT NULL,
    password VARCHAR(255),
    password_salt VARCHAR(255),
    pending_email VARCHAR(255),
    pending_email_verification_code VARCHAR(255),
    pending_email_old_verification_code VARCHAR(255),
    pending_email_old_verified boolean,
    is_password_temporary boolean DEFAULT false NOT NULL,
    temporary_password_expires_at TIMESTAMP,
    CONSTRAINT app_user_multifactor_authentication_type_check CHECK ((multifactor_authentication_type IN ('SMS', 'EMAIL', 'PASSKEY', 'PASSWORD_RESET')))
);


--
-- Name: app_user_settings; Type: TABLE
--

CREATE TABLE app_user_settings (
    auto_preview_documents boolean NOT NULL,
    notify_doc_add boolean NOT NULL,
    notify_doc_comment boolean NOT NULL,
    notify_doc_delete boolean NOT NULL,
    notify_doc_upload boolean NOT NULL,
    notify_login boolean NOT NULL,
    notify_share_accept boolean NOT NULL,
    notify_share_decline boolean NOT NULL,
    notify_share_end boolean NOT NULL,
    notify_share_start boolean NOT NULL,
    created_date TIMESTAMP(6) NOT NULL,
    updated_date TIMESTAMP(6) NOT NULL,
    id uuid NOT NULL,
    theme VARCHAR(16) DEFAULT 'light' NOT NULL,
    tour_completed boolean DEFAULT false NOT NULL,
    CONSTRAINT app_user_settings_theme_chk CHECK ((theme IN ('light', 'dark', 'system')))
);


--
-- Name: application; Type: TABLE
--

CREATE TABLE application (
    is_active boolean NOT NULL,
    created_date TIMESTAMP(6) NOT NULL,
    last_access_date TIMESTAMP(6),
    id uuid NOT NULL,
    api_key VARCHAR(255) NOT NULL,
    api_secret VARCHAR(255) NOT NULL,
    role_name VARCHAR(32) DEFAULT 'APPLICATION' NOT NULL,
    application_type VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    CONSTRAINT application_application_type_check CHECK ((application_type IN ('WEB', 'MOBILE', 'SERVICE', 'INTEGRATION'))),
    CONSTRAINT application_role_name_check CHECK (role_name = 'APPLICATION')
);


--
-- Name: audit_log; Type: TABLE
--

CREATE TABLE audit_log (
    "timestamp" TIMESTAMP(6) NOT NULL,
    document_id uuid NOT NULL,
    id uuid NOT NULL,
    performed_by_app_user_id uuid,
    action VARCHAR(255) NOT NULL,
    performed_by_email VARCHAR(255) NOT NULL,
    CONSTRAINT audit_log_action_check CHECK ((action IN ('UPLOAD', 'DOWNLOAD', 'VIEW', 'CREATED', 'DELETE', 'UPDATE', 'COMMENT', 'VERSION_CREATED')))
);


--
-- Name: auth_audit_event; Type: TABLE
--

CREATE TABLE auth_audit_event (
    created_date TIMESTAMP(6) NOT NULL,
    actor_id uuid,
    id uuid NOT NULL,
    organization_id uuid,
    event_hash VARCHAR(128) NOT NULL,
    prev_event_hash VARCHAR(128),
    action_reason VARCHAR(2048),
    after_snapshot VARCHAR(4000),
    before_snapshot VARCHAR(4000),
    action VARCHAR(255) NOT NULL,
    actor_role VARCHAR(255),
    outcome VARCHAR(255) NOT NULL,
    reason_code VARCHAR(255),
    request_id VARCHAR(255),
    exchange_id VARCHAR(255),
    target_id VARCHAR(255),
    target_type VARCHAR(255)
);


--
-- Name: auth_token; Type: TABLE
--

CREATE TABLE auth_token (
    created_date TIMESTAMP(6),
    expiry_date TIMESTAMP(6),
    app_user_id uuid,
    id uuid NOT NULL,
    token VARCHAR(2048) NOT NULL,
    jti VARCHAR(255),
    otp VARCHAR(255),
    token_type VARCHAR(255),
    CONSTRAINT auth_token_token_type_check CHECK ((token_type IN ('ACCESS', 'REFRESH', 'ID')))
);


--
-- Name: contact_details; Type: TABLE
--

CREATE TABLE contact_details (
    is_email_verified boolean,
    is_phone_verified boolean,
    created_date TIMESTAMP(6),
    id uuid NOT NULL,
    organization_id uuid,
    person_id uuid,
    email VARCHAR(255),
    email_verification_code VARCHAR(255),
    pending_email VARCHAR(255),
    pending_phone_number VARCHAR(255),
    phone_number VARCHAR(255),
    phone_verification_code VARCHAR(255)
);


--
-- Name: document; Type: TABLE
--

CREATE TABLE document (
    encryption_mode smallint NOT NULL,
    is_deleted boolean NOT NULL,
    restricted_type smallint,
    created_date TIMESTAMP(6) NOT NULL,
    update_date TIMESTAMP(6) NOT NULL,
    upload_date TIMESTAMP(6),
    id uuid NOT NULL,
    last_updated_by_id uuid,
    hash VARCHAR(255),
    title VARCHAR(255) NOT NULL,
    type VARCHAR(255),
    CONSTRAINT document_encryption_mode_check CHECK (((encryption_mode >= 0) AND (encryption_mode <= 1))),
    CONSTRAINT document_restricted_type_check CHECK (((restricted_type >= 0) AND (restricted_type <= 8))),
    CONSTRAINT document_type_check CHECK ((type IN ('PDF', 'DOCX', 'DOC', 'XLSX', 'XLS', 'PPTX', 'PPT', 'PNG', 'JPG')))
);


--
-- Name: document_comment; Type: TABLE
--

CREATE TABLE document_comment (
    created_date TIMESTAMP(6) NOT NULL,
    commented_by_user_id uuid NOT NULL,
    document_id uuid NOT NULL,
    id uuid NOT NULL,
    comment_text VARCHAR(500) NOT NULL
);


--
-- Name: document_version; Type: TABLE
--

CREATE TABLE document_version (
    created_date TIMESTAMP(6) NOT NULL,
    created_by uuid,
    document_id uuid,
    id uuid NOT NULL,
    createdbyemail VARCHAR(255),
    file_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(255) NOT NULL,
    version VARCHAR(255) NOT NULL
);


--
-- Name: exchange; Type: TABLE
--

CREATE TABLE exchange (
    is_deleted boolean NOT NULL,
    require_recipient_sign_in boolean NOT NULL,
    created_date TIMESTAMP(6) NOT NULL,
    date_deleted TIMESTAMP(6),
    end_date TIMESTAMP(6),
    expire_date TIMESTAMP(6),
    last_activity TIMESTAMP(6) NOT NULL,
    id uuid NOT NULL,
    initiator_id uuid,
    description VARCHAR(255) NOT NULL,
    end_note VARCHAR(255),
    initial_share_message VARCHAR(255) NOT NULL,
    rejection_reason VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    recipient_otp_hash VARCHAR(255),
    recipient_otp_expiry TIMESTAMP(6),
    no_auth_access_verified_at TIMESTAMP(6),
    no_auth_access_validity_days integer DEFAULT 7 NOT NULL,
    CONSTRAINT exchange_status_check CHECK ((status IN ('INITIATED', 'ACCEPTED_STARTED', 'ENDED', 'REJECTED')))
);


--
-- Name: exchange_document; Type: TABLE
--

CREATE TABLE exchange_document (
    exchange_id uuid NOT NULL,
    documents_id uuid NOT NULL
);


--
-- Name: external_participant; Type: TABLE
--

CREATE TABLE external_participant (
    id uuid NOT NULL,
    owner_organization_id uuid,
    email VARCHAR(255) NOT NULL,
    email_lower VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    email_verified_at TIMESTAMP(6),
    last_seen_at TIMESTAMP(6),
    is_active boolean DEFAULT true NOT NULL,
    created_date TIMESTAMP(6) NOT NULL
);


--
-- Name: identity_provider_link; Type: TABLE
--

CREATE TABLE identity_provider_link (
    created_date TIMESTAMP(6) NOT NULL,
    app_user_id uuid NOT NULL,
    id uuid NOT NULL,
    external_email VARCHAR(255) NOT NULL,
    external_subject_id VARCHAR(255) NOT NULL,
    provider VARCHAR(255) NOT NULL,
    CONSTRAINT identity_provider_link_provider_check CHECK ((provider IN ('INTERNAL', 'MICROSOFT', 'GOOGLE')))
);


--
-- Name: in_app_notification; Type: TABLE
--

CREATE TABLE in_app_notification (
    id uuid NOT NULL,
    app_user_id uuid NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body VARCHAR(2048),
    payload_json text,
    is_read boolean DEFAULT false NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    read_at TIMESTAMP(6)
);


--
-- Name: mfa_record; Type: TABLE
--

CREATE TABLE mfa_record (
    attempt_count integer,
    created_date TIMESTAMP(6),
    expiry_date TIMESTAMP(6),
    app_user_id uuid,
    id uuid NOT NULL,
    ip_address VARCHAR(255),
    mfa_token VARCHAR(255),
    mfa_type VARCHAR(255),
    exchange_id VARCHAR(255),
    status VARCHAR(255),
    action_description VARCHAR(255),
    CONSTRAINT mfa_record_mfa_type_check CHECK ((mfa_type IN ('SMS', 'EMAIL', 'PASSKEY', 'PASSWORD_RESET'))),
    CONSTRAINT mfa_record_status_check CHECK ((status IN ('PENDING', 'COMPLETED', 'LOCKED')))
);


--
-- Name: notification_delivery_log; Type: TABLE
--

CREATE TABLE notification_delivery_log (
    id uuid NOT NULL,
    event_id uuid NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    app_user_id uuid,
    channel VARCHAR(32) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    attempt_number integer DEFAULT 1 NOT NULL,
    error_message VARCHAR(2048),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT notification_delivery_log_outcome_check CHECK ((outcome IN ('DELIVERED', 'FAILED', 'SUPPRESSED', 'QUIET_HOURS', 'FALLBACK', 'SKIPPED')))
);


--
-- Name: notification_preference; Type: TABLE
--

CREATE TABLE notification_preference (
    id uuid NOT NULL,
    app_user_id uuid NOT NULL,
    event_pattern VARCHAR(128) NOT NULL,
    channels VARCHAR(512) NOT NULL,
    delivery VARCHAR(32) NOT NULL,
    quiet_hours_start VARCHAR(8),
    quiet_hours_end VARCHAR(8),
    timezone VARCHAR(64),
    is_active boolean DEFAULT true NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT notification_preference_delivery_check CHECK ((delivery IN ('INSTANT', 'DIGEST_HOURLY', 'DIGEST_DAILY')))
);


--
-- Name: notification_rule; Type: TABLE
--

CREATE TABLE notification_rule (
    id uuid NOT NULL,
    scope VARCHAR(32) NOT NULL,
    organization_id uuid,
    event_pattern VARCHAR(128) NOT NULL,
    predicate_json text,
    assignees_json text NOT NULL,
    action VARCHAR(32) NOT NULL,
    priority integer DEFAULT 100 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT ck_notif_rule_scope CHECK (((((scope) = 'APP') AND (organization_id IS NULL)) OR (((scope) = 'ORG') AND (organization_id IS NOT NULL)))),
    CONSTRAINT notification_rule_action_check CHECK ((action IN ('NOTIFY', 'SUPPRESS'))),
    CONSTRAINT notification_rule_scope_check CHECK ((scope IN ('APP', 'ORG')))
);


--
-- Name: organization; Type: TABLE
--

CREATE TABLE organization (
    is_active boolean NOT NULL,
    verification_complete boolean NOT NULL,
    created_date TIMESTAMP(6) NOT NULL,
    contact_details_id uuid,
    id uuid NOT NULL,
    settings_id uuid,
    name VARCHAR(255) NOT NULL,
    registration_number VARCHAR(255) NOT NULL
);


--
-- Name: organization_exchange_link; Type: TABLE
--

CREATE TABLE organization_exchange_link (
    created_date TIMESTAMP(6) NOT NULL,
    linked_date TIMESTAMP(6),
    rejected_date TIMESTAMP(6),
    id uuid NOT NULL,
    requested_organization_id uuid NOT NULL,
    requesting_organization_id uuid NOT NULL,
    rejection_reason VARCHAR(255),
    requesting_message VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    CONSTRAINT organization_exchange_link_status_check CHECK ((status IN ('PENDING', 'ACCEPTED', 'REJECTED')))
);


--
-- Name: organization_identity_provider_config; Type: TABLE
--

CREATE TABLE organization_identity_provider_config (
    is_active boolean NOT NULL,
    access_token_expiry_minutes bigint,
    created_date TIMESTAMP(6) NOT NULL,
    max_exchange_duration_hours bigint,
    updated_date TIMESTAMP(6) NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    updated_by uuid,
    allowed_algs VARCHAR(512),
    scopes VARCHAR(1024),
    allowed_audiences VARCHAR(2048),
    required_claims VARCHAR(2048),
    client_id VARCHAR(255),
    client_secret_ref VARCHAR(255),
    oidc_issuer VARCHAR(255),
    provider VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255),
    refresh_token_expiry_minutes bigint,
    idle_timeout_minutes bigint
);


--
-- Name: organization_membership; Type: TABLE
--

CREATE TABLE organization_membership (
    id uuid NOT NULL,
    app_user_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    status VARCHAR(32) NOT NULL,
    is_primary boolean DEFAULT false NOT NULL,
    joined_at TIMESTAMP(6) NOT NULL,
    invited_by_app_user_id uuid,
    expires_at TIMESTAMP(6),
    deprovisioned_at TIMESTAMP(6),
    created_date TIMESTAMP(6) NOT NULL,
    CONSTRAINT organization_membership_status_check CHECK ((status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'LEFT')))
);


--
-- Name: organization_membership_role; Type: TABLE
--

CREATE TABLE organization_membership_role (
    organization_membership_id uuid NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    CONSTRAINT organization_membership_role_name_check CHECK (
        role_name IN (
            'ORG_OWNER',
            'ORG_ADMIN',
            'ORG_BILLING_ADMIN',
            'ORG_USER_MANAGER',
            'ORG_AUDITOR',
            'ORG_MEMBER',
            'ORG_GUEST'
        )
    )
);


--
-- Name: organization_notification_channel; Type: TABLE
--

CREATE TABLE organization_notification_channel (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    channel VARCHAR(32) NOT NULL,
    config_json text,
    secret_ref VARCHAR(255),
    enforced boolean DEFAULT false NOT NULL,
    fallback_chain VARCHAR(256),
    is_active boolean DEFAULT true NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT organization_notification_channel_channel_check CHECK ((channel IN ('SLACK', 'TEAMS', 'WHATSAPP')))
);


--
-- Name: organization_settings; Type: TABLE
--

CREATE TABLE organization_settings (
    allow_email_update boolean NOT NULL,
    allow_profile_update boolean NOT NULL,
    allow_share_without_pairing boolean NOT NULL,
    created_date TIMESTAMP(6) NOT NULL,
    updated_date TIMESTAMP(6) NOT NULL,
    id uuid NOT NULL,
    allow_external_customer_sharing boolean DEFAULT true NOT NULL,
    require_recipient_acceptance boolean DEFAULT true NOT NULL
);


--
-- Name: organization_subscription_policy; Type: TABLE
--

CREATE TABLE organization_subscription_policy (
    created_date TIMESTAMP(6) NOT NULL,
    max_users bigint,
    updated_date TIMESTAMP(6) NOT NULL,
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    change_reason VARCHAR(1024),
    tier_code VARCHAR(255) NOT NULL
);


--
-- Name: person; Type: TABLE
--

CREATE TABLE person (
    created_date TIMESTAMP(6),
    contact_details_id uuid,
    id uuid NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    identification_number VARCHAR(255),
    last_name VARCHAR(255) NOT NULL,
    person_id_type VARCHAR(255),
    CONSTRAINT person_person_id_type_check CHECK ((person_id_type IN ('ID_NUMBER', 'PASSPORT_NUMBER', 'SOCIAL_SECURITY')))
);


--
-- Name: principal_group; Type: TABLE
--

CREATE TABLE principal_group (
    id uuid NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1024),
    scope VARCHAR(32) NOT NULL,
    owner_organization_id uuid,
    owner_app_user_id uuid,
    parent_group_id uuid,
    externally_published boolean DEFAULT false NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_date TIMESTAMP(6) NOT NULL,
    CONSTRAINT ck_pgroup_scope CHECK (((((scope) = 'ORG') AND (owner_organization_id IS NOT NULL) AND (owner_app_user_id IS NULL)) OR (((scope) = 'PERSONAL') AND (owner_app_user_id IS NOT NULL) AND (owner_organization_id IS NULL)) OR ((scope) = 'SHARED_PROJECT'))),
    CONSTRAINT principal_group_scope_check CHECK ((scope IN ('ORG', 'PERSONAL', 'SHARED_PROJECT')))
);


--
-- Name: principal_group_co_owner_org; Type: TABLE
--

CREATE TABLE principal_group_co_owner_org (
    principal_group_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    added_at TIMESTAMP(6) NOT NULL
);


--
-- Name: principal_group_member; Type: TABLE
--

CREATE TABLE principal_group_member (
    id uuid NOT NULL,
    principal_group_id uuid NOT NULL,
    principal_kind VARCHAR(32) NOT NULL,
    principal_id uuid NOT NULL,
    group_role VARCHAR(32) NOT NULL,
    added_by_app_user_id uuid,
    added_at TIMESTAMP(6) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    CONSTRAINT principal_group_member_group_role_check CHECK ((group_role IN ('OWNER', 'MANAGER', 'MEMBER', 'OBSERVER'))),
    CONSTRAINT principal_group_member_principal_kind_check CHECK ((principal_kind IN ('USER', 'PARTICIPANT')))
);


--
-- Name: refresh_token; Type: TABLE
--

CREATE TABLE refresh_token (
    consumed_at TIMESTAMP(6),
    expires_at TIMESTAMP(6) NOT NULL,
    grace_until TIMESTAMP(6),
    issued_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6),
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    user_exchange_id uuid,
    token_hash VARCHAR(128) NOT NULL,
    family_id VARCHAR(255) NOT NULL,
    jti VARCHAR(255) NOT NULL,
    revocation_reason_code VARCHAR(255),
    rotated_from_jti VARCHAR(255),
    status VARCHAR(255) NOT NULL
);


--
-- Name: app_role_assignment; Type: TABLE
--

CREATE TABLE app_role_assignment (
    id uuid NOT NULL,
    app_user_id uuid NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    granted_by_app_user_id uuid,
    granted_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6),
    is_active boolean DEFAULT true NOT NULL,
    CONSTRAINT app_role_assignment_role_name_check CHECK (
        role_name IN ('APP_ADMIN', 'APP_AUDITOR', 'APP_SUPPORT', 'APP_USER')
    )
);


--
-- Name: security_incident; Type: TABLE
--

CREATE TABLE security_incident (
    created_date TIMESTAMP(6) NOT NULL,
    actor_id uuid,
    id uuid NOT NULL,
    details VARCHAR(2048),
    incident_type VARCHAR(255) NOT NULL,
    request_id VARCHAR(255),
    severity VARCHAR(255) NOT NULL
);


--
-- Name: service_account; Type: TABLE
--

CREATE TABLE service_account (
    id uuid NOT NULL,
    organization_id uuid,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1024),
    scopes VARCHAR(2048),
    is_active boolean DEFAULT true NOT NULL,
    created_by uuid,
    created_date TIMESTAMP(6) NOT NULL
);


--
-- Name: share; Type: TABLE
--

CREATE TABLE share (
    id uuid NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id uuid NOT NULL,
    principal_kind VARCHAR(32) NOT NULL,
    principal_id uuid NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    source VARCHAR(32) NOT NULL,
    source_share_id uuid,
    status VARCHAR(32) NOT NULL,
    granted_by_app_user_id uuid,
    granted_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6),
    revoked_at TIMESTAMP(6),
    revoked_by_app_user_id uuid,
    constraints_json text,
    CONSTRAINT ck_share_expiry CHECK (((expires_at IS NULL) OR (expires_at > granted_at))),
    CONSTRAINT share_principal_kind_check CHECK ((principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION', 'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK'))),
    CONSTRAINT share_role_name_check CHECK ((role_name IN ('OWNER', 'EDITOR', 'REVIEWER', 'SIGNER', 'VIEWER', 'COMMENTER', 'PARTICIPANT'))),
    CONSTRAINT share_resource_type_check CHECK ((resource_type IN ('EXCHANGE', 'DOCUMENT', 'PRINCIPAL_GROUP'))),
    CONSTRAINT share_source_check CHECK ((source IN ('DIRECT', 'INVITE', 'LINK', 'INHERITED_FROM_GROUP', 'INHERITED_FROM_ORG'))),
    CONSTRAINT share_status_check CHECK ((status IN ('PENDING_APPROVAL', 'ACTIVE', 'REVOKED', 'EXPIRED')))
);


--
-- Name: share_link; Type: TABLE
--

CREATE TABLE share_link (
    id uuid NOT NULL,
    share_id uuid NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    password_hash VARCHAR(255),
    max_uses integer,
    used_count integer DEFAULT 0 NOT NULL,
    domain_allowlist VARCHAR(2048),
    require_mfa boolean DEFAULT false NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP(6),
    created_by_app_user_id uuid,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT share_link_status_check CHECK ((status IN ('ACTIVE', 'REVOKED', 'EXPIRED')))
);


--
-- Name: sign_up; Type: TABLE
--

CREATE TABLE sign_up (
    otp_attempts integer NOT NULL,
    otp_regeneration_attempts integer NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    last_regeneration_attempt_time TIMESTAMP(6),
    otp_expiry_timestamp TIMESTAMP(6) NOT NULL,
    id uuid NOT NULL,
    email VARCHAR(255) NOT NULL,
    otp VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    CONSTRAINT sign_up_status_check CHECK ((status IN ('PENDING', 'VERIFIED', 'EXPIRED', 'EXPIRED_MAX_RETRIES', 'OTP_LOCKED')))
);


--
-- Name: user_channel_link; Type: TABLE
--

CREATE TABLE user_channel_link (
    id uuid NOT NULL,
    app_user_id uuid NOT NULL,
    organization_notification_channel_id uuid NOT NULL,
    external_account_id VARCHAR(255) NOT NULL,
    secret_ref VARCHAR(255),
    is_active boolean DEFAULT true NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);


--
-- Name: user_contact; Type: TABLE
--

CREATE TABLE user_contact (
    id uuid NOT NULL,
    owner_app_user_id uuid NOT NULL,
    contact_app_user_id uuid,
    contact_email VARCHAR(255) NOT NULL,
    contact_first_name VARCHAR(255),
    contact_last_name VARCHAR(255),
    first_shared_at TIMESTAMP(6) NOT NULL,
    last_shared_at TIMESTAMP(6) NOT NULL,
    share_count integer DEFAULT 0 NOT NULL,
    last_exchange_id uuid
);


--
-- Name: user_session; Type: TABLE
--

CREATE TABLE user_session (
    is_active boolean NOT NULL,
    created_date TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6),
    last_auth_time TIMESTAMP(6) NOT NULL,
    last_seen_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6),
    app_user_id uuid NOT NULL,
    organization_id uuid,
    revoked_by_user_id uuid,
    exchange_id uuid NOT NULL,
    risk_flags VARCHAR(1024),
    user_agent VARCHAR(1024),
    device_id VARCHAR(255),
    device_name VARCHAR(255),
    ip_address VARCHAR(255),
    revocation_reason_code VARCHAR(255)
);


--
-- Name: workflow_definition; Type: TABLE
--

CREATE TABLE workflow_definition (
    id uuid NOT NULL,
    name VARCHAR(255) NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    scope VARCHAR(32) NOT NULL,
    organization_id uuid,
    trigger_event VARCHAR(128) NOT NULL,
    steps_json text NOT NULL,
    description VARCHAR(1024),
    summary VARCHAR(512),
    general_tags text NOT NULL DEFAULT '[]',
    is_template boolean NOT NULL DEFAULT false,
    source_template_id uuid,
    is_active boolean DEFAULT true NOT NULL,
    created_by_app_user_id uuid,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT ck_workflow_def_scope CHECK (((((scope) = 'APP') AND (organization_id IS NULL)) OR (((scope) = 'ORG') AND (organization_id IS NOT NULL)))),
    CONSTRAINT workflow_definition_scope_check CHECK ((scope IN ('APP', 'ORG')))
);


--
-- Name: workflow_instance; Type: TABLE
--

CREATE TABLE workflow_instance (
    id uuid NOT NULL,
    definition_id uuid NOT NULL,
    definition_version integer NOT NULL,
    subject_resource_type VARCHAR(32),
    subject_resource_id uuid,
    organization_id uuid,
    status VARCHAR(32) NOT NULL,
    current_step_index integer DEFAULT 0 NOT NULL,
    subject_data_json text,
    initiated_by_app_user_id uuid,
    created_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6),
    CONSTRAINT workflow_instance_status_check CHECK ((status IN ('RUNNING', 'COMPLETED', 'REJECTED', 'CANCELLED', 'ESCALATED')))
);


--
-- Name: workflow_step_instance; Type: TABLE
--

CREATE TABLE workflow_step_instance (
    id uuid NOT NULL,
    instance_id uuid NOT NULL,
    step_index integer NOT NULL,
    step_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    spec_snapshot_json text NOT NULL,
    assignees_snapshot_json text,
    decisions_json text DEFAULT '[]' NOT NULL,
    addons_state_json text NOT NULL DEFAULT '{}',
    due_at TIMESTAMP(6),
    escalated_at TIMESTAMP(6),
    completed_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT workflow_step_instance_status_check CHECK ((status IN ('PENDING', 'APPROVED', 'REJECTED', 'ESCALATED', 'SKIPPED', 'COMPLETED'))),
    CONSTRAINT workflow_step_instance_step_type_check CHECK ((step_type IN ('APPROVAL', 'NOTIFICATION', 'CONDITION', 'ACTION')))
);


--
-- Name: access_audit_log access_audit_log_pkey; Type: CONSTRAINT
--

ALTER TABLE access_audit_log
    ADD CONSTRAINT access_audit_log_pkey PRIMARY KEY (id);


--
-- Name: app_user app_user_application_id_key; Type: CONSTRAINT
--

ALTER TABLE app_user
    ADD CONSTRAINT app_user_application_id_key UNIQUE (application_id);


--
-- Name: app_user app_user_person_id_key; Type: CONSTRAINT
--

ALTER TABLE app_user
    ADD CONSTRAINT app_user_person_id_key UNIQUE (person_id);


--
-- Name: app_user app_user_pkey; Type: CONSTRAINT
--

ALTER TABLE app_user
    ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);


--
-- Name: app_user app_user_settings_id_key; Type: CONSTRAINT
--

ALTER TABLE app_user
    ADD CONSTRAINT app_user_settings_id_key UNIQUE (settings_id);


--
-- Name: app_user_settings app_user_settings_pkey; Type: CONSTRAINT
--

ALTER TABLE app_user_settings
    ADD CONSTRAINT app_user_settings_pkey PRIMARY KEY (id);


--
-- Name: application application_pkey; Type: CONSTRAINT
--

ALTER TABLE application
    ADD CONSTRAINT application_pkey PRIMARY KEY (id);


--
-- Name: audit_log audit_log_pkey; Type: CONSTRAINT
--

ALTER TABLE audit_log
    ADD CONSTRAINT audit_log_pkey PRIMARY KEY (id);


--
-- Name: auth_audit_event auth_audit_event_pkey; Type: CONSTRAINT
--

ALTER TABLE auth_audit_event
    ADD CONSTRAINT auth_audit_event_pkey PRIMARY KEY (id);


--
-- Name: auth_token auth_token_jti_key; Type: CONSTRAINT
--

ALTER TABLE auth_token
    ADD CONSTRAINT auth_token_jti_key UNIQUE (jti);


--
-- Name: auth_token auth_token_pkey; Type: CONSTRAINT
--

ALTER TABLE auth_token
    ADD CONSTRAINT auth_token_pkey PRIMARY KEY (id);


--
-- Name: contact_details contact_details_organization_id_key; Type: CONSTRAINT
--

ALTER TABLE contact_details
    ADD CONSTRAINT contact_details_organization_id_key UNIQUE (organization_id);


--
-- Name: contact_details contact_details_person_id_key; Type: CONSTRAINT
--

ALTER TABLE contact_details
    ADD CONSTRAINT contact_details_person_id_key UNIQUE (person_id);


--
-- Name: contact_details contact_details_pkey; Type: CONSTRAINT
--

ALTER TABLE contact_details
    ADD CONSTRAINT contact_details_pkey PRIMARY KEY (id);


--
-- Name: document_comment document_comment_pkey; Type: CONSTRAINT
--

ALTER TABLE document_comment
    ADD CONSTRAINT document_comment_pkey PRIMARY KEY (id);



--
-- Name: document document_pkey; Type: CONSTRAINT
--

ALTER TABLE document
    ADD CONSTRAINT document_pkey PRIMARY KEY (id);


--
-- Name: document_version document_version_pkey; Type: CONSTRAINT
--

ALTER TABLE document_version
    ADD CONSTRAINT document_version_pkey PRIMARY KEY (id);


--
-- Name: exchange_document exchange_document_documents_id_key; Type: CONSTRAINT
--

ALTER TABLE exchange_document
    ADD CONSTRAINT exchange_document_documents_id_key UNIQUE (documents_id);


--
-- Name: exchange exchange_pkey; Type: CONSTRAINT
--

ALTER TABLE exchange
    ADD CONSTRAINT exchange_pkey PRIMARY KEY (id);


--
-- Name: external_participant external_participant_pkey; Type: CONSTRAINT
--

ALTER TABLE external_participant
    ADD CONSTRAINT external_participant_pkey PRIMARY KEY (id);


--
-- Name: identity_provider_link identity_provider_link_pkey; Type: CONSTRAINT
--

ALTER TABLE identity_provider_link
    ADD CONSTRAINT identity_provider_link_pkey PRIMARY KEY (id);


--
-- Name: identity_provider_link identity_provider_link_provider_external_subject_id_key; Type: CONSTRAINT
--

ALTER TABLE identity_provider_link
    ADD CONSTRAINT identity_provider_link_provider_external_subject_id_key UNIQUE (provider, external_subject_id);


--
-- Name: in_app_notification in_app_notification_pkey; Type: CONSTRAINT
--

ALTER TABLE in_app_notification
    ADD CONSTRAINT in_app_notification_pkey PRIMARY KEY (id);


--
-- Name: mfa_record mfa_record_pkey; Type: CONSTRAINT
--

ALTER TABLE mfa_record
    ADD CONSTRAINT mfa_record_pkey PRIMARY KEY (id);


--
-- Name: notification_delivery_log notification_delivery_log_pkey; Type: CONSTRAINT
--

ALTER TABLE notification_delivery_log
    ADD CONSTRAINT notification_delivery_log_pkey PRIMARY KEY (id);


--
-- Name: notification_preference notification_preference_pkey; Type: CONSTRAINT
--

ALTER TABLE notification_preference
    ADD CONSTRAINT notification_preference_pkey PRIMARY KEY (id);


--
-- Name: notification_rule notification_rule_pkey; Type: CONSTRAINT
--

ALTER TABLE notification_rule
    ADD CONSTRAINT notification_rule_pkey PRIMARY KEY (id);


--
-- Name: organization organization_contact_details_id_key; Type: CONSTRAINT
--

ALTER TABLE organization
    ADD CONSTRAINT organization_contact_details_id_key UNIQUE (contact_details_id);


--
-- Name: organization_exchange_link organization_exchange_link_pkey; Type: CONSTRAINT
--

ALTER TABLE organization_exchange_link
    ADD CONSTRAINT organization_exchange_link_pkey PRIMARY KEY (id);


--
-- Name: organization_identity_provider_config organization_identity_provider_config_pkey; Type: CONSTRAINT
--

ALTER TABLE organization_identity_provider_config
    ADD CONSTRAINT organization_identity_provider_config_pkey PRIMARY KEY (id);


--
-- Name: organization_membership organization_membership_pkey; Type: CONSTRAINT
--

ALTER TABLE organization_membership
    ADD CONSTRAINT organization_membership_pkey PRIMARY KEY (id);


ALTER TABLE organization_membership_role
    ADD CONSTRAINT organization_membership_role_pkey PRIMARY KEY (organization_membership_id, role_name);


--
-- Name: organization_notification_channel organization_notification_channel_pkey; Type: CONSTRAINT
--

ALTER TABLE organization_notification_channel
    ADD CONSTRAINT organization_notification_channel_pkey PRIMARY KEY (id);


--
-- Name: organization organization_pkey; Type: CONSTRAINT
--

ALTER TABLE organization
    ADD CONSTRAINT organization_pkey PRIMARY KEY (id);


--
-- Name: organization organization_settings_id_key; Type: CONSTRAINT
--

ALTER TABLE organization
    ADD CONSTRAINT organization_settings_id_key UNIQUE (settings_id);


--
-- Name: organization_settings organization_settings_pkey; Type: CONSTRAINT
--

ALTER TABLE organization_settings
    ADD CONSTRAINT organization_settings_pkey PRIMARY KEY (id);


--
-- Name: organization_subscription_policy organization_subscription_policy_pkey; Type: CONSTRAINT
--

ALTER TABLE organization_subscription_policy
    ADD CONSTRAINT organization_subscription_policy_pkey PRIMARY KEY (id);


--
-- Name: person person_contact_details_id_key; Type: CONSTRAINT
--

ALTER TABLE person
    ADD CONSTRAINT person_contact_details_id_key UNIQUE (contact_details_id);


--
-- Name: person person_pkey; Type: CONSTRAINT
--

ALTER TABLE person
    ADD CONSTRAINT person_pkey PRIMARY KEY (id);


--
-- Name: principal_group_co_owner_org principal_group_co_owner_org_pkey; Type: CONSTRAINT
--

ALTER TABLE principal_group_co_owner_org
    ADD CONSTRAINT principal_group_co_owner_org_pkey PRIMARY KEY (principal_group_id, organization_id);


--
-- Name: principal_group_member principal_group_member_pkey; Type: CONSTRAINT
--

ALTER TABLE principal_group_member
    ADD CONSTRAINT principal_group_member_pkey PRIMARY KEY (id);


--
-- Name: principal_group principal_group_pkey; Type: CONSTRAINT
--

ALTER TABLE principal_group
    ADD CONSTRAINT principal_group_pkey PRIMARY KEY (id);


--
-- Name: refresh_token refresh_token_jti_key; Type: CONSTRAINT
--

ALTER TABLE refresh_token
    ADD CONSTRAINT refresh_token_jti_key UNIQUE (jti);


--
-- Name: refresh_token refresh_token_pkey; Type: CONSTRAINT
--

ALTER TABLE refresh_token
    ADD CONSTRAINT refresh_token_pkey PRIMARY KEY (id);


--
-- Name: app_role_assignment app_role_assignment_pkey; Type: CONSTRAINT
--

ALTER TABLE app_role_assignment
    ADD CONSTRAINT app_role_assignment_pkey PRIMARY KEY (id);


ALTER TABLE app_role_assignment
    ADD CONSTRAINT uq_app_role_assignment_user_role UNIQUE (app_user_id, role_name);


--
-- Name: security_incident security_incident_pkey; Type: CONSTRAINT
--

ALTER TABLE security_incident
    ADD CONSTRAINT security_incident_pkey PRIMARY KEY (id);


--
-- Name: service_account service_account_pkey; Type: CONSTRAINT
--

ALTER TABLE service_account
    ADD CONSTRAINT service_account_pkey PRIMARY KEY (id);


--
-- Name: share_link share_link_pkey; Type: CONSTRAINT
--

ALTER TABLE share_link
    ADD CONSTRAINT share_link_pkey PRIMARY KEY (id);


--
-- Name: share share_pkey; Type: CONSTRAINT
--

ALTER TABLE share
    ADD CONSTRAINT share_pkey PRIMARY KEY (id);


--
-- Name: sign_up sign_up_email_key; Type: CONSTRAINT
--

ALTER TABLE sign_up
    ADD CONSTRAINT sign_up_email_key UNIQUE (email);


--
-- Name: sign_up sign_up_pkey; Type: CONSTRAINT
--

ALTER TABLE sign_up
    ADD CONSTRAINT sign_up_pkey PRIMARY KEY (id);


--
-- Name: user_contact uk_user_contact_owner_email; Type: CONSTRAINT
--

ALTER TABLE user_contact
    ADD CONSTRAINT uk_user_contact_owner_email UNIQUE (owner_app_user_id, contact_email);


--
-- Name: organization_membership uq_org_membership; Type: CONSTRAINT
--

ALTER TABLE organization_membership
    ADD CONSTRAINT uq_org_membership UNIQUE (app_user_id, organization_id);


--
-- Name: organization_notification_channel uq_org_notif_chan; Type: CONSTRAINT
--

ALTER TABLE organization_notification_channel
    ADD CONSTRAINT uq_org_notif_chan UNIQUE (organization_id, channel);


--
-- Name: principal_group_member uq_pgroup_member; Type: CONSTRAINT
--

ALTER TABLE principal_group_member
    ADD CONSTRAINT uq_pgroup_member UNIQUE (principal_group_id, principal_kind, principal_id);


--
-- Name: share_link uq_share_link_token; Type: CONSTRAINT
--

ALTER TABLE share_link
    ADD CONSTRAINT uq_share_link_token UNIQUE (token_hash);


--
-- Name: user_channel_link uq_uchan_per_channel; Type: CONSTRAINT
--

ALTER TABLE user_channel_link
    ADD CONSTRAINT uq_uchan_per_channel UNIQUE (app_user_id, organization_notification_channel_id);


--
-- Name: workflow_definition uq_workflow_def_name_version; Type: CONSTRAINT
--

ALTER TABLE workflow_definition
    ADD CONSTRAINT uq_workflow_def_name_version UNIQUE (name, version);


--
-- Name: workflow_step_instance uq_workflow_step_instance; Type: CONSTRAINT
--

ALTER TABLE workflow_step_instance
    ADD CONSTRAINT uq_workflow_step_instance UNIQUE (instance_id, step_index);


--
-- Name: user_channel_link user_channel_link_pkey; Type: CONSTRAINT
--

ALTER TABLE user_channel_link
    ADD CONSTRAINT user_channel_link_pkey PRIMARY KEY (id);


--
-- Name: user_contact user_contact_pkey; Type: CONSTRAINT
--

ALTER TABLE user_contact
    ADD CONSTRAINT user_contact_pkey PRIMARY KEY (id);


--
-- Name: user_session user_exchange_pkey; Type: CONSTRAINT
--

ALTER TABLE user_session
    ADD CONSTRAINT user_exchange_pkey PRIMARY KEY (exchange_id);


--
-- Name: workflow_definition workflow_definition_pkey; Type: CONSTRAINT
--

ALTER TABLE workflow_definition
    ADD CONSTRAINT workflow_definition_pkey PRIMARY KEY (id);


--
-- Name: workflow_instance workflow_instance_pkey; Type: CONSTRAINT
--

ALTER TABLE workflow_instance
    ADD CONSTRAINT workflow_instance_pkey PRIMARY KEY (id);


--
-- Name: workflow_step_instance workflow_step_instance_pkey; Type: CONSTRAINT
--

ALTER TABLE workflow_step_instance
    ADD CONSTRAINT workflow_step_instance_pkey PRIMARY KEY (id);


--
-- Name: ix_access_audit_actor; Type: INDEX
--

CREATE INDEX ix_access_audit_actor ON access_audit_log (actor_id, created_date);


--
-- Name: ix_access_audit_org; Type: INDEX
--

CREATE INDEX ix_access_audit_org ON access_audit_log (organization_id, created_date);


--
-- Name: ix_access_audit_resource; Type: INDEX
--

CREATE INDEX ix_access_audit_resource ON access_audit_log (target_resource_type, target_resource_id, created_date);


--
-- Name: ix_external_participant_email; Type: INDEX
--

CREATE INDEX ix_external_participant_email ON external_participant (email_lower);


--
-- Name: ix_in_app_notif_unread; Type: INDEX
--

CREATE INDEX ix_in_app_notif_unread ON in_app_notification (app_user_id, created_at DESC) WHERE (is_read = false);


--
-- Name: ix_in_app_notif_user; Type: INDEX
--

CREATE INDEX ix_in_app_notif_user ON in_app_notification (app_user_id, created_at DESC);


--
-- Name: ix_notif_log_event; Type: INDEX
--

CREATE INDEX ix_notif_log_event ON notification_delivery_log (event_id);


--
-- Name: ix_notif_log_user; Type: INDEX
--

CREATE INDEX ix_notif_log_user ON notification_delivery_log (app_user_id, created_at DESC);


--
-- Name: ix_notif_pref_user; Type: INDEX
--

CREATE INDEX ix_notif_pref_user ON notification_preference (app_user_id) WHERE (is_active = true);


--
-- Name: ix_notif_rule_lookup; Type: INDEX
--

CREATE INDEX ix_notif_rule_lookup ON notification_rule (event_pattern, scope) WHERE (is_active = true);


--
-- Name: ix_org_membership_org; Type: INDEX
--

CREATE INDEX ix_org_membership_org ON organization_membership (organization_id);


--
-- Name: ix_org_membership_user; Type: INDEX
--

CREATE INDEX ix_org_membership_user ON organization_membership (app_user_id);


--
-- Name: ix_org_notif_chan_org; Type: INDEX
--

CREATE INDEX ix_org_notif_chan_org ON organization_notification_channel (organization_id);


--
-- Name: ix_pgroup_member_group; Type: INDEX
--

CREATE INDEX ix_pgroup_member_group ON principal_group_member (principal_group_id);


--
-- Name: ix_pgroup_member_principal; Type: INDEX
--

CREATE INDEX ix_pgroup_member_principal ON principal_group_member (principal_kind, principal_id);


--
-- Name: ix_pgroup_org; Type: INDEX
--

CREATE INDEX ix_pgroup_org ON principal_group (owner_organization_id);


--
-- Name: ix_pgroup_parent; Type: INDEX
--

CREATE INDEX ix_pgroup_parent ON principal_group (parent_group_id);


--
-- Name: ix_pgroup_user; Type: INDEX
--

CREATE INDEX ix_pgroup_user ON principal_group (owner_app_user_id);


--
-- Name: ix_app_role_assignment_active; Type: INDEX
--

CREATE INDEX ix_app_role_assignment_active ON app_role_assignment (app_user_id, role_name) WHERE (is_active = true);


--
-- Name: ix_share_link_share; Type: INDEX
--

CREATE INDEX ix_share_link_share ON share_link (share_id);


--
-- Name: ix_share_pending_approval; Type: INDEX
--

CREATE INDEX ix_share_pending_approval ON share (status) WHERE ((status) = 'PENDING_APPROVAL');


--
-- Name: ix_share_principal; Type: INDEX
--

CREATE INDEX ix_share_principal ON share (principal_kind, principal_id) WHERE ((status) = 'ACTIVE');


--
-- Name: ix_share_resource; Type: INDEX
--

CREATE INDEX ix_share_resource ON share (resource_type, resource_id) WHERE ((status) = 'ACTIVE');


--
-- Name: ix_share_resource_all; Type: INDEX
--

CREATE INDEX ix_share_resource_all ON share (resource_type, resource_id);


--
-- Name: ix_user_contact_owner_last_shared; Type: INDEX
--

CREATE INDEX ix_user_contact_owner_last_shared ON user_contact (owner_app_user_id, last_shared_at);


--
-- Name: ix_workflow_def_org; Type: INDEX
--

CREATE INDEX ix_workflow_def_org ON workflow_definition (organization_id);


--
-- Name: ix_workflow_def_trigger; Type: INDEX
--

CREATE INDEX ix_workflow_def_trigger ON workflow_definition (trigger_event) WHERE (is_active = true);


--
-- Name: ix_workflow_inst_org; Type: INDEX
--

CREATE INDEX ix_workflow_inst_org ON workflow_instance (organization_id);


--
-- Name: ix_workflow_inst_status; Type: INDEX
--

CREATE INDEX ix_workflow_inst_status ON workflow_instance (status) WHERE ((status) = 'RUNNING');


--
-- Name: ix_workflow_inst_subject; Type: INDEX
--

CREATE INDEX ix_workflow_inst_subject ON workflow_instance (subject_resource_type, subject_resource_id);


--
-- Name: ix_workflow_step_inst; Type: INDEX
--

CREATE INDEX ix_workflow_step_inst ON workflow_step_instance (instance_id);


--
-- Name: ix_workflow_step_pending; Type: INDEX
--

CREATE INDEX ix_workflow_step_pending ON workflow_step_instance (status, due_at) WHERE ((status) = 'PENDING');


--
-- Name: uq_external_participant_org_email; Type: INDEX
--

CREATE UNIQUE INDEX uq_external_participant_org_email ON external_participant (COALESCE(owner_organization_id, '00000000-0000-0000-0000-000000000000'), email_lower);


--
-- Name: uq_org_membership_primary; Type: INDEX
--

CREATE UNIQUE INDEX uq_org_membership_primary ON organization_membership (app_user_id) WHERE (is_primary = true);


--
-- Name: exchange_document exchange_document_documents_id_fkey; Type: FK CONSTRAINT
--

ALTER TABLE exchange_document
    ADD CONSTRAINT exchange_document_documents_id_fkey FOREIGN KEY (documents_id) REFERENCES document(id);


--
-- Name: exchange_document exchange_document_exchange_id_fkey; Type: FK CONSTRAINT
--

ALTER TABLE exchange_document
    ADD CONSTRAINT exchange_document_exchange_id_fkey FOREIGN KEY (exchange_id) REFERENCES exchange(id);


--
-- Name: contact_details fk2yuj227wi05ujycdcs6w4hsh4; Type: FK CONSTRAINT
--

ALTER TABLE contact_details
    ADD CONSTRAINT fk2yuj227wi05ujycdcs6w4hsh4 FOREIGN KEY (person_id) REFERENCES person(id);


--
-- Name: app_user fk30lk0wcq1g873b17ktycjboxe; Type: FK CONSTRAINT
--

ALTER TABLE app_user
    ADD CONSTRAINT fk30lk0wcq1g873b17ktycjboxe FOREIGN KEY (person_id) REFERENCES person(id);


--
-- Name: document_comment fk5cqvv79u3w3slj3bysi0k4p81; Type: FK CONSTRAINT
--

ALTER TABLE document_comment
    ADD CONSTRAINT fk5cqvv79u3w3slj3bysi0k4p81 FOREIGN KEY (document_id) REFERENCES document(id);


--
-- Name: auth_token fk7pvqug8fqs5d119gxxw88f2y5; Type: FK CONSTRAINT
--

ALTER TABLE auth_token
    ADD CONSTRAINT fk7pvqug8fqs5d119gxxw88f2y5 FOREIGN KEY (app_user_id) REFERENCES app_user(id);


--
-- Name: organization fk8u9dc9o2icd9np2foou713qbe; Type: FK CONSTRAINT
--

ALTER TABLE organization
    ADD CONSTRAINT fk8u9dc9o2icd9np2foou713qbe FOREIGN KEY (settings_id) REFERENCES organization_settings(id);


--
-- Name: app_user fk905qlq4yc4ktexp3dffw6ep3l; Type: FK CONSTRAINT
--

ALTER TABLE app_user
    ADD CONSTRAINT fk905qlq4yc4ktexp3dffw6ep3l FOREIGN KEY (application_id) REFERENCES application(id);


--
-- Name: organization fk9q7w3josd040uhud64xjt8rot; Type: FK CONSTRAINT
--

ALTER TABLE organization
    ADD CONSTRAINT fk9q7w3josd040uhud64xjt8rot FOREIGN KEY (contact_details_id) REFERENCES contact_details(id);


--
-- Name: audit_log fk9qqom4bee5dqhkr9n933xu01e; Type: FK CONSTRAINT
--

ALTER TABLE audit_log
    ADD CONSTRAINT fk9qqom4bee5dqhkr9n933xu01e FOREIGN KEY (document_id) REFERENCES document(id);


--
-- Name: external_participant fk_external_participant_org; Type: FK CONSTRAINT
--

ALTER TABLE external_participant
    ADD CONSTRAINT fk_external_participant_org FOREIGN KEY (owner_organization_id) REFERENCES organization(id);


--
-- Name: in_app_notification fk_in_app_notif_user; Type: FK CONSTRAINT
--

ALTER TABLE in_app_notification
    ADD CONSTRAINT fk_in_app_notif_user FOREIGN KEY (app_user_id) REFERENCES app_user(id);


--
-- Name: notification_preference fk_notif_pref_user; Type: FK CONSTRAINT
--

ALTER TABLE notification_preference
    ADD CONSTRAINT fk_notif_pref_user FOREIGN KEY (app_user_id) REFERENCES app_user(id);


--
-- Name: notification_rule fk_notif_rule_org; Type: FK CONSTRAINT
--

ALTER TABLE notification_rule
    ADD CONSTRAINT fk_notif_rule_org FOREIGN KEY (organization_id) REFERENCES organization(id);


--
-- Name: organization_membership fk_org_membership_invited; Type: FK CONSTRAINT
--

ALTER TABLE organization_membership
    ADD CONSTRAINT fk_org_membership_invited FOREIGN KEY (invited_by_app_user_id) REFERENCES app_user(id);


--
-- Name: organization_membership fk_org_membership_org; Type: FK CONSTRAINT
--

ALTER TABLE organization_membership
    ADD CONSTRAINT fk_org_membership_org FOREIGN KEY (organization_id) REFERENCES organization(id);


--
-- Name: organization_membership fk_org_membership_user; Type: FK CONSTRAINT
--

ALTER TABLE organization_membership
    ADD CONSTRAINT fk_org_membership_user FOREIGN KEY (app_user_id) REFERENCES app_user(id);


--
-- Name: organization_notification_channel fk_org_notif_chan_org; Type: FK CONSTRAINT
--

ALTER TABLE organization_notification_channel
    ADD CONSTRAINT fk_org_notif_chan_org FOREIGN KEY (organization_id) REFERENCES organization(id);


--
-- Name: principal_group_co_owner_org fk_pgroup_coowner_group; Type: FK CONSTRAINT
--

ALTER TABLE principal_group_co_owner_org
    ADD CONSTRAINT fk_pgroup_coowner_group FOREIGN KEY (principal_group_id) REFERENCES principal_group(id);


--
-- Name: principal_group_co_owner_org fk_pgroup_coowner_org; Type: FK CONSTRAINT
--

ALTER TABLE principal_group_co_owner_org
    ADD CONSTRAINT fk_pgroup_coowner_org FOREIGN KEY (organization_id) REFERENCES organization(id);


--
-- Name: principal_group_member fk_pgroup_member_addedby; Type: FK CONSTRAINT
--

ALTER TABLE principal_group_member
    ADD CONSTRAINT fk_pgroup_member_addedby FOREIGN KEY (added_by_app_user_id) REFERENCES app_user(id);


--
-- Name: principal_group_member fk_pgroup_member_group; Type: FK CONSTRAINT
--

ALTER TABLE principal_group_member
    ADD CONSTRAINT fk_pgroup_member_group FOREIGN KEY (principal_group_id) REFERENCES principal_group(id);


--
-- Name: principal_group fk_pgroup_org; Type: FK CONSTRAINT
--

ALTER TABLE principal_group
    ADD CONSTRAINT fk_pgroup_org FOREIGN KEY (owner_organization_id) REFERENCES organization(id);


--
-- Name: principal_group fk_pgroup_parent; Type: FK CONSTRAINT
--

ALTER TABLE principal_group
    ADD CONSTRAINT fk_pgroup_parent FOREIGN KEY (parent_group_id) REFERENCES principal_group(id);


--
-- Name: principal_group fk_pgroup_user; Type: FK CONSTRAINT
--

ALTER TABLE principal_group
    ADD CONSTRAINT fk_pgroup_user FOREIGN KEY (owner_app_user_id) REFERENCES app_user(id);


--
-- Name: organization_membership_role fk_org_membership_role_membership; Type: FK CONSTRAINT
--

ALTER TABLE organization_membership_role
    ADD CONSTRAINT fk_org_membership_role_membership FOREIGN KEY (organization_membership_id)
        REFERENCES organization_membership(id) ON DELETE CASCADE;


--
-- Name: app_role_assignment fk_app_role_assignment_grantor; Type: FK CONSTRAINT
--

ALTER TABLE app_role_assignment
    ADD CONSTRAINT fk_app_role_assignment_grantor FOREIGN KEY (granted_by_app_user_id) REFERENCES app_user(id);


--
-- Name: app_role_assignment fk_app_role_assignment_user; Type: FK CONSTRAINT
--

ALTER TABLE app_role_assignment
    ADD CONSTRAINT fk_app_role_assignment_user FOREIGN KEY (app_user_id) REFERENCES app_user(id);


--
-- Name: service_account fk_service_account_creator; Type: FK CONSTRAINT
--

ALTER TABLE service_account
    ADD CONSTRAINT fk_service_account_creator FOREIGN KEY (created_by) REFERENCES app_user(id);


--
-- Name: service_account fk_service_account_org; Type: FK CONSTRAINT
--

ALTER TABLE service_account
    ADD CONSTRAINT fk_service_account_org FOREIGN KEY (organization_id) REFERENCES organization(id);


--
-- Name: share fk_share_grantor; Type: FK CONSTRAINT
--

ALTER TABLE share
    ADD CONSTRAINT fk_share_grantor FOREIGN KEY (granted_by_app_user_id) REFERENCES app_user(id);


--
-- Name: share_link fk_share_link_creator; Type: FK CONSTRAINT
--

ALTER TABLE share_link
    ADD CONSTRAINT fk_share_link_creator FOREIGN KEY (created_by_app_user_id) REFERENCES app_user(id);


--
-- Name: share_link fk_share_link_share; Type: FK CONSTRAINT
--

ALTER TABLE share_link
    ADD CONSTRAINT fk_share_link_share FOREIGN KEY (share_id) REFERENCES share(id);


--
-- Name: share fk_share_revoker; Type: FK CONSTRAINT
--

ALTER TABLE share
    ADD CONSTRAINT fk_share_revoker FOREIGN KEY (revoked_by_app_user_id) REFERENCES app_user(id);


--
-- Name: share fk_share_source; Type: FK CONSTRAINT
--

ALTER TABLE share
    ADD CONSTRAINT fk_share_source FOREIGN KEY (source_share_id) REFERENCES share(id);


--
-- Name: user_channel_link fk_uchan_channel; Type: FK CONSTRAINT
--

ALTER TABLE user_channel_link
    ADD CONSTRAINT fk_uchan_channel FOREIGN KEY (organization_notification_channel_id) REFERENCES organization_notification_channel(id);


--
-- Name: user_channel_link fk_uchan_user; Type: FK CONSTRAINT
--

ALTER TABLE user_channel_link
    ADD CONSTRAINT fk_uchan_user FOREIGN KEY (app_user_id) REFERENCES app_user(id);


--
-- Name: user_contact fk_user_contact_contact; Type: FK CONSTRAINT
--

ALTER TABLE user_contact
    ADD CONSTRAINT fk_user_contact_contact FOREIGN KEY (contact_app_user_id) REFERENCES app_user(id);


--
-- Name: user_contact fk_user_contact_owner; Type: FK CONSTRAINT
--

ALTER TABLE user_contact
    ADD CONSTRAINT fk_user_contact_owner FOREIGN KEY (owner_app_user_id) REFERENCES app_user(id);


--
-- Name: workflow_definition fk_workflow_def_creator; Type: FK CONSTRAINT
--

ALTER TABLE workflow_definition
    ADD CONSTRAINT fk_workflow_def_creator FOREIGN KEY (created_by_app_user_id) REFERENCES app_user(id);


--
-- Name: workflow_definition fk_workflow_def_org; Type: FK CONSTRAINT
--

ALTER TABLE workflow_definition
    ADD CONSTRAINT fk_workflow_def_org FOREIGN KEY (organization_id) REFERENCES organization(id);


--
-- Name: workflow_instance fk_workflow_inst_def; Type: FK CONSTRAINT
--

ALTER TABLE workflow_instance
    ADD CONSTRAINT fk_workflow_inst_def FOREIGN KEY (definition_id) REFERENCES workflow_definition(id);


--
-- Name: workflow_instance fk_workflow_inst_initiator; Type: FK CONSTRAINT
--

ALTER TABLE workflow_instance
    ADD CONSTRAINT fk_workflow_inst_initiator FOREIGN KEY (initiated_by_app_user_id) REFERENCES app_user(id);


--
-- Name: workflow_instance fk_workflow_inst_org; Type: FK CONSTRAINT
--

ALTER TABLE workflow_instance
    ADD CONSTRAINT fk_workflow_inst_org FOREIGN KEY (organization_id) REFERENCES organization(id);


--
-- Name: workflow_step_instance fk_workflow_step_inst; Type: FK CONSTRAINT
--

ALTER TABLE workflow_step_instance
    ADD CONSTRAINT fk_workflow_step_inst FOREIGN KEY (instance_id) REFERENCES workflow_instance(id);


--
-- Name: workflow_trigger_event_registry; Type: TABLE
-- Canonical catalogue of trigger event names exposed in the workflow designer.
--

CREATE TABLE workflow_trigger_event_registry (
    event_name          VARCHAR(128) NOT NULL,
    description         VARCHAR(512),
    subject_fields_json text NOT NULL DEFAULT '[]',
    is_active           boolean NOT NULL DEFAULT true,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT workflow_trigger_event_registry_pkey PRIMARY KEY (event_name)
);

INSERT INTO workflow_trigger_event_registry (event_name, description, subject_fields_json) VALUES
(
    'exchange.draft_submitted',
    'Runs at the start of the Exchange lifecycle, before it is sent to recipients. Use this to build internal pre-send approval workflows.',
    '[{"name":"initiatorId","type":"UUID","description":"App user who created the exchange"},
      {"name":"orgId","type":"UUID","description":"Initiator org id"}]'
),
(
    'exchange.acceptance_pending',
    'Runs after an Exchange is sent and is waiting for the recipient to accept it. Use this to notify, remind, or approve before the Exchange becomes active.',
    '[{"name":"recipientId","type":"UUID","description":"Primary recipient user id"},
      {"name":"recipientGroupId","type":"UUID","description":"Recipient group id (GROUP type only)"},
      {"name":"initiatorId","type":"UUID","description":"Initiator user id"},
      {"name":"orgId","type":"UUID","description":"Initiator org id"},
      {"name":"recipientType","type":"STRING","description":"EMAIL | APP_USER | GROUP"}]'
),
(
    'exchange.activated',
    'Runs when an Exchange becomes active after the recipient accepts it. Use this to kick off onboarding steps or notify collaborators.',
    '[{"name":"initiatorId","type":"UUID","description":"Initiator user id"},
      {"name":"orgId","type":"UUID","description":"Initiator org id"}]'
),
(
    'exchange.ending',
    'Runs when an Exchange is about to close. Use this for completion checklists, sign-off approvals, or archiving actions.',
    '[{"name":"initiatorId","type":"UUID","description":"Initiator user id"},
      {"name":"orgId","type":"UUID","description":"Initiator org id"}]'
);


ALTER TABLE user_session
    ADD CONSTRAINT fk_user_session_user FOREIGN KEY (app_user_id) REFERENCES app_user(id);

ALTER TABLE organization_subscription_policy
    ADD CONSTRAINT fk_org_sub_policy_org FOREIGN KEY (organization_id) REFERENCES organization(id);

ALTER TABLE document
    ADD CONSTRAINT fk_document_last_updated_by FOREIGN KEY (last_updated_by_id) REFERENCES app_user(id);

ALTER TABLE organization_exchange_link
    ADD CONSTRAINT fk_org_exchange_link_requesting FOREIGN KEY (requesting_organization_id) REFERENCES organization(id);

ALTER TABLE app_user
    ADD CONSTRAINT fk_app_user_org FOREIGN KEY (organization_id) REFERENCES organization(id);

ALTER TABLE mfa_record
    ADD CONSTRAINT fk_mfa_record_user FOREIGN KEY (app_user_id) REFERENCES app_user(id);

ALTER TABLE person
    ADD CONSTRAINT fk_person_contact_details FOREIGN KEY (contact_details_id) REFERENCES contact_details(id);

ALTER TABLE audit_log
    ADD CONSTRAINT fk_audit_log_performer FOREIGN KEY (performed_by_app_user_id) REFERENCES app_user(id);

ALTER TABLE exchange
    ADD CONSTRAINT fk_exchange_initiator FOREIGN KEY (initiator_id) REFERENCES app_user(id);

ALTER TABLE document_version
    ADD CONSTRAINT fk_document_version_creator FOREIGN KEY (created_by) REFERENCES app_user(id);

ALTER TABLE document_version
    ADD CONSTRAINT fk_document_version_document FOREIGN KEY (document_id) REFERENCES document(id);

ALTER TABLE document_comment
    ADD CONSTRAINT fk_document_comment_user FOREIGN KEY (commented_by_user_id) REFERENCES app_user(id);

ALTER TABLE organization_identity_provider_config
    ADD CONSTRAINT fk_org_idp_config_org FOREIGN KEY (organization_id) REFERENCES organization(id);

ALTER TABLE app_user
    ADD CONSTRAINT fk_app_user_settings FOREIGN KEY (settings_id) REFERENCES app_user_settings(id);

ALTER TABLE organization_exchange_link
    ADD CONSTRAINT fk_org_exchange_link_requested FOREIGN KEY (requested_organization_id) REFERENCES organization(id);

ALTER TABLE contact_details
    ADD CONSTRAINT fk_contact_details_org FOREIGN KEY (organization_id) REFERENCES organization(id);

ALTER TABLE identity_provider_link
    ADD CONSTRAINT fk_identity_provider_link_user FOREIGN KEY (app_user_id) REFERENCES app_user(id);

