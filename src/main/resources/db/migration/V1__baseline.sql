--
-- V1 — Consolidated baseline schema.
--
-- This squashes the original V1–V14 migration history into a single from-scratch
-- schema. The legacy sharing/collaboration model (organization_group*,
-- sharing_session_participant, admin_approval_request, sharing_session recipient/
-- permission columns, app_user.role*/organization_id) was fully cut over to the
-- unified model (share / principal_group / organization_membership / role_assignment /
-- workflow_* / notification_*), so the create-then-drop churn of V8–V14 is gone.
-- Generated mechanically via `pg_dump --schema-only` of the validated post-V14
-- schema; seed data lives in V2__seed.sql.
--
-- Pre-production, no backwards-compat owed. Safe to squash because V8–V14 were only
-- ever applied to the (now-cleared) dev DB.

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: access_audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.access_audit_log (
    id uuid NOT NULL,
    created_date timestamp(6) without time zone NOT NULL,
    action character varying(64) NOT NULL,
    outcome character varying(32) NOT NULL,
    actor_kind character varying(32) NOT NULL,
    actor_id uuid,
    actor_email character varying(255),
    target_resource_type character varying(32),
    target_resource_id uuid,
    target_principal_kind character varying(32),
    target_principal_id uuid,
    organization_id uuid,
    reason_code character varying(64),
    reason character varying(2048),
    before_snapshot text,
    after_snapshot text,
    event_hash character varying(128) NOT NULL,
    prev_event_hash character varying(128),
    CONSTRAINT access_audit_log_outcome_check CHECK (((outcome)::text = ANY ((ARRAY['ALLOW'::character varying, 'DENY'::character varying, 'GRANT'::character varying, 'REVOKE'::character varying, 'TRANSITION'::character varying])::text[])))
);


--
-- Name: app_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_user (
    email_verification_completed boolean,
    is_active boolean NOT NULL,
    is_temporary boolean NOT NULL,
    sign_in_attempts integer NOT NULL,
    created_date timestamp(6) without time zone NOT NULL,
    deprovisioned_at timestamp(6) without time zone,
    session_version bigint NOT NULL,
    application_id uuid,
    id uuid NOT NULL,
    organization_id uuid,
    person_id uuid,
    settings_id uuid,
    email character varying(255) NOT NULL,
    multifactor_authentication_type character varying(255) NOT NULL,
    password character varying(255),
    password_salt character varying(255),
    pending_email character varying(255),
    pending_email_verification_code character varying(255),
    pending_email_old_verification_code character varying(255),
    pending_email_old_verified boolean,
    is_password_temporary boolean DEFAULT false NOT NULL,
    temporary_password_expires_at timestamp without time zone,
    CONSTRAINT app_user_multifactor_authentication_type_check CHECK (((multifactor_authentication_type)::text = ANY ((ARRAY['SMS'::character varying, 'EMAIL'::character varying, 'PASSKEY'::character varying, 'PASSWORD_RESET'::character varying])::text[])))
);


--
-- Name: app_user_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_user_settings (
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
    created_date timestamp(6) without time zone NOT NULL,
    updated_date timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL,
    theme character varying(16) DEFAULT 'light'::character varying NOT NULL,
    CONSTRAINT app_user_settings_theme_chk CHECK (((theme)::text = ANY ((ARRAY['light'::character varying, 'dark'::character varying, 'system'::character varying])::text[])))
);


--
-- Name: application; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application (
    is_active boolean NOT NULL,
    created_date timestamp(6) without time zone NOT NULL,
    last_access_date timestamp(6) without time zone,
    id uuid NOT NULL,
    api_key character varying(255) NOT NULL,
    api_secret character varying(255) NOT NULL,
    application_type character varying(255) NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL,
    CONSTRAINT application_application_type_check CHECK (((application_type)::text = ANY ((ARRAY['WEB'::character varying, 'MOBILE'::character varying, 'SERVICE'::character varying, 'INTEGRATION'::character varying])::text[])))
);


--
-- Name: audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_log (
    "timestamp" timestamp(6) without time zone NOT NULL,
    document_id uuid NOT NULL,
    id uuid NOT NULL,
    performed_by_app_user_id uuid,
    action character varying(255) NOT NULL,
    performed_by_email character varying(255) NOT NULL,
    CONSTRAINT audit_log_action_check CHECK (((action)::text = ANY ((ARRAY['UPLOAD'::character varying, 'DOWNLOAD'::character varying, 'VIEW'::character varying, 'CREATED'::character varying, 'DELETE'::character varying, 'UPDATE'::character varying, 'COMMENT'::character varying, 'VERSION_CREATED'::character varying])::text[])))
);


--
-- Name: auth_audit_event; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auth_audit_event (
    created_date timestamp(6) without time zone NOT NULL,
    actor_id uuid,
    id uuid NOT NULL,
    organization_id uuid,
    event_hash character varying(128) NOT NULL,
    prev_event_hash character varying(128),
    action_reason character varying(2048),
    after_snapshot character varying(4000),
    before_snapshot character varying(4000),
    action character varying(255) NOT NULL,
    actor_role character varying(255),
    outcome character varying(255) NOT NULL,
    reason_code character varying(255),
    request_id character varying(255),
    session_id character varying(255),
    target_id character varying(255),
    target_type character varying(255)
);


--
-- Name: auth_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auth_token (
    created_date timestamp(6) without time zone,
    expiry_date timestamp(6) without time zone,
    app_user_id uuid,
    id uuid NOT NULL,
    token character varying(2048) NOT NULL,
    jti character varying(255),
    otp character varying(255),
    token_type character varying(255),
    CONSTRAINT auth_token_token_type_check CHECK (((token_type)::text = ANY ((ARRAY['ACCESS'::character varying, 'REFRESH'::character varying, 'ID'::character varying])::text[])))
);


--
-- Name: contact_details; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contact_details (
    is_email_verified boolean,
    is_phone_verified boolean,
    created_date timestamp(6) without time zone,
    id uuid NOT NULL,
    organization_id uuid,
    person_id uuid,
    email character varying(255),
    email_verification_code character varying(255),
    pending_email character varying(255),
    pending_phone_number character varying(255),
    phone_number character varying(255),
    phone_verification_code character varying(255)
);


--
-- Name: document; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.document (
    encryption_mode smallint NOT NULL,
    is_deleted boolean NOT NULL,
    restricted_type smallint,
    created_date timestamp(6) without time zone NOT NULL,
    update_date timestamp(6) without time zone NOT NULL,
    upload_date timestamp(6) without time zone,
    id uuid NOT NULL,
    last_updated_by_id uuid,
    hash character varying(255),
    title character varying(255) NOT NULL,
    type character varying(255),
    CONSTRAINT document_encryption_mode_check CHECK (((encryption_mode >= 0) AND (encryption_mode <= 1))),
    CONSTRAINT document_restricted_type_check CHECK (((restricted_type >= 0) AND (restricted_type <= 8))),
    CONSTRAINT document_type_check CHECK (((type)::text = ANY ((ARRAY['PDF'::character varying, 'DOCX'::character varying, 'DOC'::character varying, 'XLSX'::character varying, 'XLS'::character varying, 'PPTX'::character varying, 'PPT'::character varying, 'PNG'::character varying, 'JPG'::character varying])::text[])))
);


--
-- Name: document_comment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.document_comment (
    created_date timestamp(6) without time zone NOT NULL,
    commented_by_user_id uuid NOT NULL,
    document_id uuid NOT NULL,
    id uuid NOT NULL,
    comment_text character varying(500) NOT NULL
);


--
-- Name: document_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.document_version (
    created_date timestamp(6) without time zone NOT NULL,
    created_by uuid,
    document_id uuid,
    id uuid NOT NULL,
    createdbyemail character varying(255),
    file_name character varying(255) NOT NULL,
    storage_path character varying(255) NOT NULL,
    version character varying(255) NOT NULL
);


--
-- Name: external_participant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.external_participant (
    id uuid NOT NULL,
    owner_organization_id uuid,
    email character varying(255) NOT NULL,
    email_lower character varying(255) NOT NULL,
    display_name character varying(255),
    email_verified_at timestamp(6) without time zone,
    last_seen_at timestamp(6) without time zone,
    is_active boolean DEFAULT true NOT NULL,
    created_date timestamp(6) without time zone NOT NULL
);


--
-- Name: identity_provider_link; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.identity_provider_link (
    created_date timestamp(6) without time zone NOT NULL,
    app_user_id uuid NOT NULL,
    id uuid NOT NULL,
    external_email character varying(255) NOT NULL,
    external_subject_id character varying(255) NOT NULL,
    provider character varying(255) NOT NULL,
    CONSTRAINT identity_provider_link_provider_check CHECK (((provider)::text = ANY ((ARRAY['INTERNAL'::character varying, 'MICROSOFT'::character varying, 'GOOGLE'::character varying])::text[])))
);


--
-- Name: in_app_notification; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.in_app_notification (
    id uuid NOT NULL,
    app_user_id uuid NOT NULL,
    event_type character varying(128) NOT NULL,
    title character varying(255) NOT NULL,
    body character varying(2048),
    payload_json text,
    is_read boolean DEFAULT false NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    read_at timestamp(6) without time zone
);


--
-- Name: mfa_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mfa_record (
    attempt_count integer,
    created_date timestamp(6) without time zone,
    expiry_date timestamp(6) without time zone,
    app_user_id uuid,
    id uuid NOT NULL,
    ip_address character varying(255),
    mfa_token character varying(255),
    mfa_type character varying(255),
    session_id character varying(255),
    status character varying(255),
    CONSTRAINT mfa_record_mfa_type_check CHECK (((mfa_type)::text = ANY ((ARRAY['SMS'::character varying, 'EMAIL'::character varying, 'PASSKEY'::character varying, 'PASSWORD_RESET'::character varying])::text[]))),
    CONSTRAINT mfa_record_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'COMPLETED'::character varying, 'LOCKED'::character varying])::text[])))
);


--
-- Name: notification_delivery_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_delivery_log (
    id uuid NOT NULL,
    event_id uuid NOT NULL,
    event_type character varying(128) NOT NULL,
    app_user_id uuid,
    channel character varying(32) NOT NULL,
    outcome character varying(32) NOT NULL,
    attempt_number integer DEFAULT 1 NOT NULL,
    error_message character varying(2048),
    created_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT notification_delivery_log_outcome_check CHECK (((outcome)::text = ANY ((ARRAY['DELIVERED'::character varying, 'FAILED'::character varying, 'SUPPRESSED'::character varying, 'QUIET_HOURS'::character varying, 'FALLBACK'::character varying, 'SKIPPED'::character varying])::text[])))
);


--
-- Name: notification_preference; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_preference (
    id uuid NOT NULL,
    app_user_id uuid NOT NULL,
    event_pattern character varying(128) NOT NULL,
    channels character varying(512) NOT NULL,
    delivery character varying(32) NOT NULL,
    quiet_hours_start character varying(8),
    quiet_hours_end character varying(8),
    timezone character varying(64),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT notification_preference_delivery_check CHECK (((delivery)::text = ANY ((ARRAY['INSTANT'::character varying, 'DIGEST_HOURLY'::character varying, 'DIGEST_DAILY'::character varying])::text[])))
);


--
-- Name: notification_rule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_rule (
    id uuid NOT NULL,
    scope character varying(32) NOT NULL,
    organization_id uuid,
    event_pattern character varying(128) NOT NULL,
    predicate_json text,
    assignees_json text NOT NULL,
    action character varying(32) NOT NULL,
    priority integer DEFAULT 100 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT ck_notif_rule_scope CHECK (((((scope)::text = 'APP'::text) AND (organization_id IS NULL)) OR (((scope)::text = 'ORG'::text) AND (organization_id IS NOT NULL)))),
    CONSTRAINT notification_rule_action_check CHECK (((action)::text = ANY ((ARRAY['NOTIFY'::character varying, 'SUPPRESS'::character varying])::text[]))),
    CONSTRAINT notification_rule_scope_check CHECK (((scope)::text = ANY ((ARRAY['APP'::character varying, 'ORG'::character varying])::text[])))
);


--
-- Name: organization; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organization (
    is_active boolean NOT NULL,
    verification_complete boolean NOT NULL,
    created_date timestamp(6) without time zone NOT NULL,
    contact_details_id uuid,
    id uuid NOT NULL,
    settings_id uuid,
    name character varying(255) NOT NULL,
    registration_number character varying(255) NOT NULL
);


--
-- Name: organization_identity_provider_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organization_identity_provider_config (
    is_active boolean NOT NULL,
    access_token_expiry_minutes bigint,
    created_date timestamp(6) without time zone NOT NULL,
    max_session_duration_hours bigint,
    updated_date timestamp(6) without time zone NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    updated_by uuid,
    allowed_algs character varying(512),
    scopes character varying(1024),
    allowed_audiences character varying(2048),
    required_claims character varying(2048),
    client_id character varying(255),
    client_secret_ref character varying(255),
    oidc_issuer character varying(255),
    provider character varying(255) NOT NULL,
    tenant_id character varying(255),
    refresh_token_expiry_minutes bigint,
    idle_timeout_minutes bigint
);


--
-- Name: organization_membership; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organization_membership (
    id uuid NOT NULL,
    app_user_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    role_name character varying(64) NOT NULL,
    status character varying(32) NOT NULL,
    is_primary boolean DEFAULT false NOT NULL,
    joined_at timestamp(6) without time zone NOT NULL,
    invited_by_app_user_id uuid,
    expires_at timestamp(6) without time zone,
    deprovisioned_at timestamp(6) without time zone,
    created_date timestamp(6) without time zone NOT NULL,
    CONSTRAINT organization_membership_status_check CHECK (((status)::text = ANY ((ARRAY['INVITED'::character varying, 'ACTIVE'::character varying, 'SUSPENDED'::character varying, 'LEFT'::character varying])::text[])))
);


--
-- Name: organization_notification_channel; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organization_notification_channel (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    channel character varying(32) NOT NULL,
    config_json text,
    secret_ref character varying(255),
    enforced boolean DEFAULT false NOT NULL,
    fallback_chain character varying(256),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT organization_notification_channel_channel_check CHECK (((channel)::text = ANY ((ARRAY['SLACK'::character varying, 'TEAMS'::character varying, 'WHATSAPP'::character varying])::text[])))
);


--
-- Name: organization_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organization_settings (
    allow_email_update boolean NOT NULL,
    allow_profile_update boolean NOT NULL,
    allow_share_without_pairing boolean NOT NULL,
    created_date timestamp(6) without time zone NOT NULL,
    updated_date timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL
);


--
-- Name: organization_sharing_session_link; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organization_sharing_session_link (
    created_date timestamp(6) without time zone NOT NULL,
    linked_date timestamp(6) without time zone,
    rejected_date timestamp(6) without time zone,
    id uuid NOT NULL,
    requested_organization_id uuid NOT NULL,
    requesting_organization_id uuid NOT NULL,
    rejection_reason character varying(255),
    requesting_message character varying(255),
    status character varying(255) NOT NULL,
    CONSTRAINT organization_sharing_session_link_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACCEPTED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: organization_subscription_policy; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organization_subscription_policy (
    created_date timestamp(6) without time zone NOT NULL,
    max_users bigint,
    updated_date timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    change_reason character varying(1024),
    tier_code character varying(255) NOT NULL
);


--
-- Name: person; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.person (
    created_date timestamp(6) without time zone,
    contact_details_id uuid,
    id uuid NOT NULL,
    first_name character varying(255) NOT NULL,
    identification_number character varying(255),
    last_name character varying(255) NOT NULL,
    person_id_type character varying(255),
    CONSTRAINT person_person_id_type_check CHECK (((person_id_type)::text = ANY ((ARRAY['ID_NUMBER'::character varying, 'PASSPORT_NUMBER'::character varying, 'SOCIAL_SECURITY'::character varying])::text[])))
);


--
-- Name: principal_group; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.principal_group (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(1024),
    scope character varying(32) NOT NULL,
    owner_organization_id uuid,
    owner_app_user_id uuid,
    parent_group_id uuid,
    externally_published boolean DEFAULT false NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_date timestamp(6) without time zone NOT NULL,
    CONSTRAINT ck_pgroup_scope CHECK (((((scope)::text = 'ORG'::text) AND (owner_organization_id IS NOT NULL) AND (owner_app_user_id IS NULL)) OR (((scope)::text = 'PERSONAL'::text) AND (owner_app_user_id IS NOT NULL) AND (owner_organization_id IS NULL)) OR ((scope)::text = 'SHARED_PROJECT'::text))),
    CONSTRAINT principal_group_scope_check CHECK (((scope)::text = ANY ((ARRAY['ORG'::character varying, 'PERSONAL'::character varying, 'SHARED_PROJECT'::character varying])::text[])))
);


--
-- Name: principal_group_co_owner_org; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.principal_group_co_owner_org (
    principal_group_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    added_at timestamp(6) without time zone NOT NULL
);


--
-- Name: principal_group_member; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.principal_group_member (
    id uuid NOT NULL,
    principal_group_id uuid NOT NULL,
    principal_kind character varying(32) NOT NULL,
    principal_id uuid NOT NULL,
    group_role character varying(32) NOT NULL,
    added_by_app_user_id uuid,
    added_at timestamp(6) without time zone NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    CONSTRAINT principal_group_member_group_role_check CHECK (((group_role)::text = ANY ((ARRAY['OWNER'::character varying, 'MANAGER'::character varying, 'MEMBER'::character varying, 'OBSERVER'::character varying])::text[]))),
    CONSTRAINT principal_group_member_principal_kind_check CHECK (((principal_kind)::text = ANY ((ARRAY['USER'::character varying, 'PARTICIPANT'::character varying])::text[])))
);


--
-- Name: refresh_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_token (
    consumed_at timestamp(6) without time zone,
    expires_at timestamp(6) without time zone NOT NULL,
    grace_until timestamp(6) without time zone,
    issued_at timestamp(6) without time zone NOT NULL,
    revoked_at timestamp(6) without time zone,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    user_session_id uuid,
    token_hash character varying(128) NOT NULL,
    family_id character varying(255) NOT NULL,
    jti character varying(255) NOT NULL,
    revocation_reason_code character varying(255),
    rotated_from_jti character varying(255),
    status character varying(255) NOT NULL
);


--
-- Name: role_assignment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_assignment (
    id uuid NOT NULL,
    app_user_id uuid,
    service_account_id uuid,
    role_name character varying(64) NOT NULL,
    scope_type character varying(32) NOT NULL,
    scope_id uuid,
    granted_by_app_user_id uuid,
    granted_at timestamp(6) without time zone NOT NULL,
    expires_at timestamp(6) without time zone,
    is_active boolean DEFAULT true NOT NULL,
    CONSTRAINT ck_role_assignment_scope CHECK (((((scope_type)::text = 'APP'::text) AND (scope_id IS NULL)) OR (((scope_type)::text <> 'APP'::text) AND (scope_id IS NOT NULL)))),
    CONSTRAINT ck_role_assignment_subject CHECK ((((app_user_id IS NOT NULL) AND (service_account_id IS NULL)) OR ((app_user_id IS NULL) AND (service_account_id IS NOT NULL)))),
    CONSTRAINT role_assignment_scope_type_check CHECK (((scope_type)::text = ANY ((ARRAY['APP'::character varying, 'ORG'::character varying, 'PRINCIPAL_GROUP'::character varying, 'RESOURCE'::character varying])::text[])))
);


--
-- Name: security_incident; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.security_incident (
    created_date timestamp(6) without time zone NOT NULL,
    actor_id uuid,
    id uuid NOT NULL,
    details character varying(2048),
    incident_type character varying(255) NOT NULL,
    request_id character varying(255),
    severity character varying(255) NOT NULL
);


--
-- Name: service_account; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.service_account (
    id uuid NOT NULL,
    organization_id uuid,
    name character varying(255) NOT NULL,
    description character varying(1024),
    scopes character varying(2048),
    is_active boolean DEFAULT true NOT NULL,
    created_by uuid,
    created_date timestamp(6) without time zone NOT NULL
);


--
-- Name: share; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.share (
    id uuid NOT NULL,
    resource_type character varying(32) NOT NULL,
    resource_id uuid NOT NULL,
    principal_kind character varying(32) NOT NULL,
    principal_id uuid NOT NULL,
    role_name character varying(64) NOT NULL,
    source character varying(32) NOT NULL,
    source_share_id uuid,
    status character varying(32) NOT NULL,
    granted_by_app_user_id uuid,
    granted_at timestamp(6) without time zone NOT NULL,
    expires_at timestamp(6) without time zone,
    revoked_at timestamp(6) without time zone,
    revoked_by_app_user_id uuid,
    constraints_json text,
    CONSTRAINT ck_share_expiry CHECK (((expires_at IS NULL) OR (expires_at > granted_at))),
    CONSTRAINT share_principal_kind_check CHECK (((principal_kind)::text = ANY ((ARRAY['USER'::character varying, 'PARTICIPANT'::character varying, 'PRINCIPAL_GROUP'::character varying, 'ORGANIZATION'::character varying, 'SERVICE_ACCOUNT'::character varying, 'PUBLIC_LINK'::character varying])::text[]))),
    CONSTRAINT share_resource_type_check CHECK (((resource_type)::text = ANY ((ARRAY['SHARING_SESSION'::character varying, 'DOCUMENT'::character varying, 'PRINCIPAL_GROUP'::character varying])::text[]))),
    CONSTRAINT share_source_check CHECK (((source)::text = ANY ((ARRAY['DIRECT'::character varying, 'INVITE'::character varying, 'LINK'::character varying, 'INHERITED_FROM_GROUP'::character varying, 'INHERITED_FROM_ORG'::character varying])::text[]))),
    CONSTRAINT share_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING_APPROVAL'::character varying, 'ACTIVE'::character varying, 'REVOKED'::character varying, 'EXPIRED'::character varying])::text[])))
);


--
-- Name: share_link; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.share_link (
    id uuid NOT NULL,
    share_id uuid NOT NULL,
    token_hash character varying(128) NOT NULL,
    password_hash character varying(255),
    max_uses integer,
    used_count integer DEFAULT 0 NOT NULL,
    domain_allowlist character varying(2048),
    require_mfa boolean DEFAULT false NOT NULL,
    status character varying(32) NOT NULL,
    expires_at timestamp(6) without time zone,
    created_by_app_user_id uuid,
    created_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT share_link_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'REVOKED'::character varying, 'EXPIRED'::character varying])::text[])))
);


--
-- Name: sharing_session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sharing_session (
    is_deleted boolean NOT NULL,
    require_recipient_sign_in boolean NOT NULL,
    created_date timestamp(6) without time zone NOT NULL,
    date_deleted timestamp(6) without time zone,
    end_date timestamp(6) without time zone,
    expire_date timestamp(6) without time zone,
    last_activity timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL,
    initiator_id uuid,
    description character varying(255) NOT NULL,
    end_note character varying(255),
    initial_share_message character varying(255) NOT NULL,
    rejection_reason character varying(255),
    session_name character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    recipient_otp_hash character varying(255),
    recipient_otp_expiry timestamp(6) without time zone,
    no_auth_access_verified_at timestamp(6) without time zone,
    no_auth_access_validity_days integer DEFAULT 7 NOT NULL,
    CONSTRAINT sharing_session_status_check CHECK (((status)::text = ANY ((ARRAY['INITIATED'::character varying, 'ACCEPTED_STARTED'::character varying, 'ENDED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: sharing_session_document; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sharing_session_document (
    sharingsession_id uuid NOT NULL,
    documents_id uuid NOT NULL
);


--
-- Name: sign_up; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sign_up (
    otp_attempts integer NOT NULL,
    otp_regeneration_attempts integer NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    last_regeneration_attempt_time timestamp(6) without time zone,
    otp_expiry_timestamp timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL,
    email character varying(255) NOT NULL,
    otp character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    CONSTRAINT sign_up_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'VERIFIED'::character varying, 'EXPIRED'::character varying, 'EXPIRED_MAX_RETRIES'::character varying, 'OTP_LOCKED'::character varying])::text[])))
);


--
-- Name: user_channel_link; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_channel_link (
    id uuid NOT NULL,
    app_user_id uuid NOT NULL,
    organization_notification_channel_id uuid NOT NULL,
    external_account_id character varying(255) NOT NULL,
    secret_ref character varying(255),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(6) without time zone NOT NULL
);


--
-- Name: user_contact; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_contact (
    id uuid NOT NULL,
    owner_app_user_id uuid NOT NULL,
    contact_app_user_id uuid,
    contact_email character varying(255) NOT NULL,
    contact_first_name character varying(255),
    contact_last_name character varying(255),
    first_shared_at timestamp(6) without time zone NOT NULL,
    last_shared_at timestamp(6) without time zone NOT NULL,
    share_count integer DEFAULT 0 NOT NULL,
    last_session_id uuid
);


--
-- Name: user_session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_session (
    is_active boolean NOT NULL,
    created_date timestamp(6) without time zone NOT NULL,
    expires_at timestamp(6) without time zone,
    last_auth_time timestamp(6) without time zone NOT NULL,
    last_seen_at timestamp(6) without time zone NOT NULL,
    revoked_at timestamp(6) without time zone,
    app_user_id uuid NOT NULL,
    organization_id uuid,
    revoked_by_user_id uuid,
    session_id uuid NOT NULL,
    risk_flags character varying(1024),
    user_agent character varying(1024),
    device_id character varying(255),
    device_name character varying(255),
    ip_address character varying(255),
    revocation_reason_code character varying(255)
);


--
-- Name: workflow_definition; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workflow_definition (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    scope character varying(32) NOT NULL,
    organization_id uuid,
    trigger_event character varying(128) NOT NULL,
    steps_json text NOT NULL,
    description character varying(1024),
    is_active boolean DEFAULT true NOT NULL,
    created_by_app_user_id uuid,
    created_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT ck_workflow_def_scope CHECK (((((scope)::text = 'APP'::text) AND (organization_id IS NULL)) OR (((scope)::text = 'ORG'::text) AND (organization_id IS NOT NULL)))),
    CONSTRAINT workflow_definition_scope_check CHECK (((scope)::text = ANY ((ARRAY['APP'::character varying, 'ORG'::character varying])::text[])))
);


--
-- Name: workflow_instance; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workflow_instance (
    id uuid NOT NULL,
    definition_id uuid NOT NULL,
    definition_version integer NOT NULL,
    subject_resource_type character varying(32),
    subject_resource_id uuid,
    organization_id uuid,
    status character varying(32) NOT NULL,
    current_step_index integer DEFAULT 0 NOT NULL,
    subject_data_json text,
    initiated_by_app_user_id uuid,
    created_at timestamp(6) without time zone NOT NULL,
    completed_at timestamp(6) without time zone,
    CONSTRAINT workflow_instance_status_check CHECK (((status)::text = ANY ((ARRAY['RUNNING'::character varying, 'COMPLETED'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying, 'ESCALATED'::character varying])::text[])))
);


--
-- Name: workflow_step_instance; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workflow_step_instance (
    id uuid NOT NULL,
    instance_id uuid NOT NULL,
    step_index integer NOT NULL,
    step_type character varying(32) NOT NULL,
    status character varying(32) NOT NULL,
    spec_snapshot_json text NOT NULL,
    assignees_snapshot_json text,
    decisions_json text DEFAULT '[]'::text NOT NULL,
    due_at timestamp(6) without time zone,
    escalated_at timestamp(6) without time zone,
    completed_at timestamp(6) without time zone,
    created_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT workflow_step_instance_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'ESCALATED'::character varying, 'SKIPPED'::character varying, 'COMPLETED'::character varying])::text[]))),
    CONSTRAINT workflow_step_instance_step_type_check CHECK (((step_type)::text = ANY ((ARRAY['APPROVAL'::character varying, 'NOTIFICATION'::character varying, 'CONDITION'::character varying, 'ACTION'::character varying])::text[])))
);


--
-- Name: access_audit_log access_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.access_audit_log
    ADD CONSTRAINT access_audit_log_pkey PRIMARY KEY (id);


--
-- Name: app_user app_user_application_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_application_id_key UNIQUE (application_id);


--
-- Name: app_user app_user_person_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_person_id_key UNIQUE (person_id);


--
-- Name: app_user app_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);


--
-- Name: app_user app_user_settings_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_settings_id_key UNIQUE (settings_id);


--
-- Name: app_user_settings app_user_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user_settings
    ADD CONSTRAINT app_user_settings_pkey PRIMARY KEY (id);


--
-- Name: application application_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application
    ADD CONSTRAINT application_pkey PRIMARY KEY (id);


--
-- Name: audit_log audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT audit_log_pkey PRIMARY KEY (id);


--
-- Name: auth_audit_event auth_audit_event_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_audit_event
    ADD CONSTRAINT auth_audit_event_pkey PRIMARY KEY (id);


--
-- Name: auth_token auth_token_jti_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_token
    ADD CONSTRAINT auth_token_jti_key UNIQUE (jti);


--
-- Name: auth_token auth_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_token
    ADD CONSTRAINT auth_token_pkey PRIMARY KEY (id);


--
-- Name: contact_details contact_details_organization_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contact_details
    ADD CONSTRAINT contact_details_organization_id_key UNIQUE (organization_id);


--
-- Name: contact_details contact_details_person_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contact_details
    ADD CONSTRAINT contact_details_person_id_key UNIQUE (person_id);


--
-- Name: contact_details contact_details_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contact_details
    ADD CONSTRAINT contact_details_pkey PRIMARY KEY (id);


--
-- Name: document_comment document_comment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_comment
    ADD CONSTRAINT document_comment_pkey PRIMARY KEY (id);


--
-- Name: document document_last_updated_by_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document
    ADD CONSTRAINT document_last_updated_by_id_key UNIQUE (last_updated_by_id);


--
-- Name: document document_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document
    ADD CONSTRAINT document_pkey PRIMARY KEY (id);


--
-- Name: document_version document_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_version
    ADD CONSTRAINT document_version_pkey PRIMARY KEY (id);


--
-- Name: external_participant external_participant_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.external_participant
    ADD CONSTRAINT external_participant_pkey PRIMARY KEY (id);


--
-- Name: identity_provider_link identity_provider_link_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.identity_provider_link
    ADD CONSTRAINT identity_provider_link_pkey PRIMARY KEY (id);


--
-- Name: identity_provider_link identity_provider_link_provider_external_subject_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.identity_provider_link
    ADD CONSTRAINT identity_provider_link_provider_external_subject_id_key UNIQUE (provider, external_subject_id);


--
-- Name: in_app_notification in_app_notification_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.in_app_notification
    ADD CONSTRAINT in_app_notification_pkey PRIMARY KEY (id);


--
-- Name: mfa_record mfa_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mfa_record
    ADD CONSTRAINT mfa_record_pkey PRIMARY KEY (id);


--
-- Name: notification_delivery_log notification_delivery_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_delivery_log
    ADD CONSTRAINT notification_delivery_log_pkey PRIMARY KEY (id);


--
-- Name: notification_preference notification_preference_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_preference
    ADD CONSTRAINT notification_preference_pkey PRIMARY KEY (id);


--
-- Name: notification_rule notification_rule_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_rule
    ADD CONSTRAINT notification_rule_pkey PRIMARY KEY (id);


--
-- Name: organization organization_contact_details_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization
    ADD CONSTRAINT organization_contact_details_id_key UNIQUE (contact_details_id);


--
-- Name: organization_identity_provider_config organization_identity_provider_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_identity_provider_config
    ADD CONSTRAINT organization_identity_provider_config_pkey PRIMARY KEY (id);


--
-- Name: organization_membership organization_membership_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_membership
    ADD CONSTRAINT organization_membership_pkey PRIMARY KEY (id);


--
-- Name: organization_notification_channel organization_notification_channel_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_notification_channel
    ADD CONSTRAINT organization_notification_channel_pkey PRIMARY KEY (id);


--
-- Name: organization organization_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization
    ADD CONSTRAINT organization_pkey PRIMARY KEY (id);


--
-- Name: organization organization_settings_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization
    ADD CONSTRAINT organization_settings_id_key UNIQUE (settings_id);


--
-- Name: organization_settings organization_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_settings
    ADD CONSTRAINT organization_settings_pkey PRIMARY KEY (id);


--
-- Name: organization_sharing_session_link organization_sharing_session_link_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_sharing_session_link
    ADD CONSTRAINT organization_sharing_session_link_pkey PRIMARY KEY (id);


--
-- Name: organization_subscription_policy organization_subscription_policy_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_subscription_policy
    ADD CONSTRAINT organization_subscription_policy_pkey PRIMARY KEY (id);


--
-- Name: person person_contact_details_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.person
    ADD CONSTRAINT person_contact_details_id_key UNIQUE (contact_details_id);


--
-- Name: person person_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.person
    ADD CONSTRAINT person_pkey PRIMARY KEY (id);


--
-- Name: principal_group_co_owner_org principal_group_co_owner_org_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.principal_group_co_owner_org
    ADD CONSTRAINT principal_group_co_owner_org_pkey PRIMARY KEY (principal_group_id, organization_id);


--
-- Name: principal_group_member principal_group_member_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.principal_group_member
    ADD CONSTRAINT principal_group_member_pkey PRIMARY KEY (id);


--
-- Name: principal_group principal_group_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.principal_group
    ADD CONSTRAINT principal_group_pkey PRIMARY KEY (id);


--
-- Name: refresh_token refresh_token_jti_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_token
    ADD CONSTRAINT refresh_token_jti_key UNIQUE (jti);


--
-- Name: refresh_token refresh_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_token
    ADD CONSTRAINT refresh_token_pkey PRIMARY KEY (id);


--
-- Name: role_assignment role_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_assignment
    ADD CONSTRAINT role_assignment_pkey PRIMARY KEY (id);


--
-- Name: security_incident security_incident_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.security_incident
    ADD CONSTRAINT security_incident_pkey PRIMARY KEY (id);


--
-- Name: service_account service_account_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_account
    ADD CONSTRAINT service_account_pkey PRIMARY KEY (id);


--
-- Name: share_link share_link_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share_link
    ADD CONSTRAINT share_link_pkey PRIMARY KEY (id);


--
-- Name: share share_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share
    ADD CONSTRAINT share_pkey PRIMARY KEY (id);


--
-- Name: sharing_session_document sharing_session_document_documents_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sharing_session_document
    ADD CONSTRAINT sharing_session_document_documents_id_key UNIQUE (documents_id);


--
-- Name: sharing_session sharing_session_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sharing_session
    ADD CONSTRAINT sharing_session_pkey PRIMARY KEY (id);


--
-- Name: sign_up sign_up_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sign_up
    ADD CONSTRAINT sign_up_email_key UNIQUE (email);


--
-- Name: sign_up sign_up_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sign_up
    ADD CONSTRAINT sign_up_pkey PRIMARY KEY (id);


--
-- Name: user_contact uk_user_contact_owner_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_contact
    ADD CONSTRAINT uk_user_contact_owner_email UNIQUE (owner_app_user_id, contact_email);


--
-- Name: organization_membership uq_org_membership; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_membership
    ADD CONSTRAINT uq_org_membership UNIQUE (app_user_id, organization_id);


--
-- Name: organization_notification_channel uq_org_notif_chan; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_notification_channel
    ADD CONSTRAINT uq_org_notif_chan UNIQUE (organization_id, channel);


--
-- Name: principal_group_member uq_pgroup_member; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.principal_group_member
    ADD CONSTRAINT uq_pgroup_member UNIQUE (principal_group_id, principal_kind, principal_id);


--
-- Name: share_link uq_share_link_token; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share_link
    ADD CONSTRAINT uq_share_link_token UNIQUE (token_hash);


--
-- Name: user_channel_link uq_uchan_per_channel; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_channel_link
    ADD CONSTRAINT uq_uchan_per_channel UNIQUE (app_user_id, organization_notification_channel_id);


--
-- Name: workflow_definition uq_workflow_def_name_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_definition
    ADD CONSTRAINT uq_workflow_def_name_version UNIQUE (name, version);


--
-- Name: workflow_step_instance uq_workflow_step_instance; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_step_instance
    ADD CONSTRAINT uq_workflow_step_instance UNIQUE (instance_id, step_index);


--
-- Name: user_channel_link user_channel_link_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_channel_link
    ADD CONSTRAINT user_channel_link_pkey PRIMARY KEY (id);


--
-- Name: user_contact user_contact_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_contact
    ADD CONSTRAINT user_contact_pkey PRIMARY KEY (id);


--
-- Name: user_session user_session_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_session
    ADD CONSTRAINT user_session_pkey PRIMARY KEY (session_id);


--
-- Name: workflow_definition workflow_definition_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_definition
    ADD CONSTRAINT workflow_definition_pkey PRIMARY KEY (id);


--
-- Name: workflow_instance workflow_instance_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_instance
    ADD CONSTRAINT workflow_instance_pkey PRIMARY KEY (id);


--
-- Name: workflow_step_instance workflow_step_instance_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_step_instance
    ADD CONSTRAINT workflow_step_instance_pkey PRIMARY KEY (id);


--
-- Name: ix_access_audit_actor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_access_audit_actor ON public.access_audit_log USING btree (actor_id, created_date);


--
-- Name: ix_access_audit_org; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_access_audit_org ON public.access_audit_log USING btree (organization_id, created_date);


--
-- Name: ix_access_audit_resource; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_access_audit_resource ON public.access_audit_log USING btree (target_resource_type, target_resource_id, created_date);


--
-- Name: ix_external_participant_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_external_participant_email ON public.external_participant USING btree (email_lower);


--
-- Name: ix_in_app_notif_unread; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_in_app_notif_unread ON public.in_app_notification USING btree (app_user_id, created_at DESC) WHERE (is_read = false);


--
-- Name: ix_in_app_notif_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_in_app_notif_user ON public.in_app_notification USING btree (app_user_id, created_at DESC);


--
-- Name: ix_notif_log_event; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_notif_log_event ON public.notification_delivery_log USING btree (event_id);


--
-- Name: ix_notif_log_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_notif_log_user ON public.notification_delivery_log USING btree (app_user_id, created_at DESC);


--
-- Name: ix_notif_pref_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_notif_pref_user ON public.notification_preference USING btree (app_user_id) WHERE (is_active = true);


--
-- Name: ix_notif_rule_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_notif_rule_lookup ON public.notification_rule USING btree (event_pattern, scope) WHERE (is_active = true);


--
-- Name: ix_org_membership_org; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_org_membership_org ON public.organization_membership USING btree (organization_id);


--
-- Name: ix_org_membership_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_org_membership_user ON public.organization_membership USING btree (app_user_id);


--
-- Name: ix_org_notif_chan_org; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_org_notif_chan_org ON public.organization_notification_channel USING btree (organization_id);


--
-- Name: ix_pgroup_member_group; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_pgroup_member_group ON public.principal_group_member USING btree (principal_group_id);


--
-- Name: ix_pgroup_member_principal; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_pgroup_member_principal ON public.principal_group_member USING btree (principal_kind, principal_id);


--
-- Name: ix_pgroup_org; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_pgroup_org ON public.principal_group USING btree (owner_organization_id);


--
-- Name: ix_pgroup_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_pgroup_parent ON public.principal_group USING btree (parent_group_id);


--
-- Name: ix_pgroup_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_pgroup_user ON public.principal_group USING btree (owner_app_user_id);


--
-- Name: ix_role_assignment_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_role_assignment_scope ON public.role_assignment USING btree (scope_type, scope_id) WHERE (is_active = true);


--
-- Name: ix_role_assignment_svc_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_role_assignment_svc_scope ON public.role_assignment USING btree (service_account_id, scope_type, scope_id) WHERE (is_active = true);


--
-- Name: ix_role_assignment_user_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_role_assignment_user_scope ON public.role_assignment USING btree (app_user_id, scope_type, scope_id) WHERE (is_active = true);


--
-- Name: ix_share_link_share; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_share_link_share ON public.share_link USING btree (share_id);


--
-- Name: ix_share_pending_approval; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_share_pending_approval ON public.share USING btree (status) WHERE ((status)::text = 'PENDING_APPROVAL'::text);


--
-- Name: ix_share_principal; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_share_principal ON public.share USING btree (principal_kind, principal_id) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: ix_share_resource; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_share_resource ON public.share USING btree (resource_type, resource_id) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: ix_share_resource_all; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_share_resource_all ON public.share USING btree (resource_type, resource_id);


--
-- Name: ix_user_contact_owner_last_shared; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_user_contact_owner_last_shared ON public.user_contact USING btree (owner_app_user_id, last_shared_at);


--
-- Name: ix_workflow_def_org; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_workflow_def_org ON public.workflow_definition USING btree (organization_id);


--
-- Name: ix_workflow_def_trigger; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_workflow_def_trigger ON public.workflow_definition USING btree (trigger_event) WHERE (is_active = true);


--
-- Name: ix_workflow_inst_org; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_workflow_inst_org ON public.workflow_instance USING btree (organization_id);


--
-- Name: ix_workflow_inst_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_workflow_inst_status ON public.workflow_instance USING btree (status) WHERE ((status)::text = 'RUNNING'::text);


--
-- Name: ix_workflow_inst_subject; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_workflow_inst_subject ON public.workflow_instance USING btree (subject_resource_type, subject_resource_id);


--
-- Name: ix_workflow_step_inst; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_workflow_step_inst ON public.workflow_step_instance USING btree (instance_id);


--
-- Name: ix_workflow_step_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_workflow_step_pending ON public.workflow_step_instance USING btree (status, due_at) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: uq_external_participant_org_email; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_external_participant_org_email ON public.external_participant USING btree (COALESCE(owner_organization_id, '00000000-0000-0000-0000-000000000000'::uuid), email_lower);


--
-- Name: uq_org_membership_primary; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_org_membership_primary ON public.organization_membership USING btree (app_user_id) WHERE (is_primary = true);


--
-- Name: contact_details fk2yuj227wi05ujycdcs6w4hsh4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contact_details
    ADD CONSTRAINT fk2yuj227wi05ujycdcs6w4hsh4 FOREIGN KEY (person_id) REFERENCES public.person(id);


--
-- Name: app_user fk30lk0wcq1g873b17ktycjboxe; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT fk30lk0wcq1g873b17ktycjboxe FOREIGN KEY (person_id) REFERENCES public.person(id);


--
-- Name: document_comment fk5cqvv79u3w3slj3bysi0k4p81; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_comment
    ADD CONSTRAINT fk5cqvv79u3w3slj3bysi0k4p81 FOREIGN KEY (document_id) REFERENCES public.document(id);


--
-- Name: auth_token fk7pvqug8fqs5d119gxxw88f2y5; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_token
    ADD CONSTRAINT fk7pvqug8fqs5d119gxxw88f2y5 FOREIGN KEY (app_user_id) REFERENCES public.app_user(id);


--
-- Name: sharing_session_document fk7vocc392sve21f24l77wgh0ae; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sharing_session_document
    ADD CONSTRAINT fk7vocc392sve21f24l77wgh0ae FOREIGN KEY (sharingsession_id) REFERENCES public.sharing_session(id);


--
-- Name: organization fk8u9dc9o2icd9np2foou713qbe; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization
    ADD CONSTRAINT fk8u9dc9o2icd9np2foou713qbe FOREIGN KEY (settings_id) REFERENCES public.organization_settings(id);


--
-- Name: app_user fk905qlq4yc4ktexp3dffw6ep3l; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT fk905qlq4yc4ktexp3dffw6ep3l FOREIGN KEY (application_id) REFERENCES public.application(id);


--
-- Name: organization fk9q7w3josd040uhud64xjt8rot; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization
    ADD CONSTRAINT fk9q7w3josd040uhud64xjt8rot FOREIGN KEY (contact_details_id) REFERENCES public.contact_details(id);


--
-- Name: audit_log fk9qqom4bee5dqhkr9n933xu01e; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT fk9qqom4bee5dqhkr9n933xu01e FOREIGN KEY (document_id) REFERENCES public.document(id);


--
-- Name: external_participant fk_external_participant_org; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.external_participant
    ADD CONSTRAINT fk_external_participant_org FOREIGN KEY (owner_organization_id) REFERENCES public.organization(id);


--
-- Name: in_app_notification fk_in_app_notif_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.in_app_notification
    ADD CONSTRAINT fk_in_app_notif_user FOREIGN KEY (app_user_id) REFERENCES public.app_user(id);


--
-- Name: notification_preference fk_notif_pref_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_preference
    ADD CONSTRAINT fk_notif_pref_user FOREIGN KEY (app_user_id) REFERENCES public.app_user(id);


--
-- Name: notification_rule fk_notif_rule_org; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_rule
    ADD CONSTRAINT fk_notif_rule_org FOREIGN KEY (organization_id) REFERENCES public.organization(id);


--
-- Name: organization_membership fk_org_membership_invited; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_membership
    ADD CONSTRAINT fk_org_membership_invited FOREIGN KEY (invited_by_app_user_id) REFERENCES public.app_user(id);


--
-- Name: organization_membership fk_org_membership_org; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_membership
    ADD CONSTRAINT fk_org_membership_org FOREIGN KEY (organization_id) REFERENCES public.organization(id);


--
-- Name: organization_membership fk_org_membership_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_membership
    ADD CONSTRAINT fk_org_membership_user FOREIGN KEY (app_user_id) REFERENCES public.app_user(id);


--
-- Name: organization_notification_channel fk_org_notif_chan_org; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_notification_channel
    ADD CONSTRAINT fk_org_notif_chan_org FOREIGN KEY (organization_id) REFERENCES public.organization(id);


--
-- Name: principal_group_co_owner_org fk_pgroup_coowner_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.principal_group_co_owner_org
    ADD CONSTRAINT fk_pgroup_coowner_group FOREIGN KEY (principal_group_id) REFERENCES public.principal_group(id);


--
-- Name: principal_group_co_owner_org fk_pgroup_coowner_org; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.principal_group_co_owner_org
    ADD CONSTRAINT fk_pgroup_coowner_org FOREIGN KEY (organization_id) REFERENCES public.organization(id);


--
-- Name: principal_group_member fk_pgroup_member_addedby; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.principal_group_member
    ADD CONSTRAINT fk_pgroup_member_addedby FOREIGN KEY (added_by_app_user_id) REFERENCES public.app_user(id);


--
-- Name: principal_group_member fk_pgroup_member_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.principal_group_member
    ADD CONSTRAINT fk_pgroup_member_group FOREIGN KEY (principal_group_id) REFERENCES public.principal_group(id);


--
-- Name: principal_group fk_pgroup_org; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.principal_group
    ADD CONSTRAINT fk_pgroup_org FOREIGN KEY (owner_organization_id) REFERENCES public.organization(id);


--
-- Name: principal_group fk_pgroup_parent; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.principal_group
    ADD CONSTRAINT fk_pgroup_parent FOREIGN KEY (parent_group_id) REFERENCES public.principal_group(id);


--
-- Name: principal_group fk_pgroup_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.principal_group
    ADD CONSTRAINT fk_pgroup_user FOREIGN KEY (owner_app_user_id) REFERENCES public.app_user(id);


--
-- Name: role_assignment fk_role_assignment_grantor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_assignment
    ADD CONSTRAINT fk_role_assignment_grantor FOREIGN KEY (granted_by_app_user_id) REFERENCES public.app_user(id);


--
-- Name: role_assignment fk_role_assignment_svc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_assignment
    ADD CONSTRAINT fk_role_assignment_svc FOREIGN KEY (service_account_id) REFERENCES public.service_account(id);


--
-- Name: role_assignment fk_role_assignment_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_assignment
    ADD CONSTRAINT fk_role_assignment_user FOREIGN KEY (app_user_id) REFERENCES public.app_user(id);


--
-- Name: service_account fk_service_account_creator; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_account
    ADD CONSTRAINT fk_service_account_creator FOREIGN KEY (created_by) REFERENCES public.app_user(id);


--
-- Name: service_account fk_service_account_org; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_account
    ADD CONSTRAINT fk_service_account_org FOREIGN KEY (organization_id) REFERENCES public.organization(id);


--
-- Name: share fk_share_grantor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share
    ADD CONSTRAINT fk_share_grantor FOREIGN KEY (granted_by_app_user_id) REFERENCES public.app_user(id);


--
-- Name: share_link fk_share_link_creator; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share_link
    ADD CONSTRAINT fk_share_link_creator FOREIGN KEY (created_by_app_user_id) REFERENCES public.app_user(id);


--
-- Name: share_link fk_share_link_share; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share_link
    ADD CONSTRAINT fk_share_link_share FOREIGN KEY (share_id) REFERENCES public.share(id);


--
-- Name: share fk_share_revoker; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share
    ADD CONSTRAINT fk_share_revoker FOREIGN KEY (revoked_by_app_user_id) REFERENCES public.app_user(id);


--
-- Name: share fk_share_source; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share
    ADD CONSTRAINT fk_share_source FOREIGN KEY (source_share_id) REFERENCES public.share(id);


--
-- Name: user_channel_link fk_uchan_channel; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_channel_link
    ADD CONSTRAINT fk_uchan_channel FOREIGN KEY (organization_notification_channel_id) REFERENCES public.organization_notification_channel(id);


--
-- Name: user_channel_link fk_uchan_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_channel_link
    ADD CONSTRAINT fk_uchan_user FOREIGN KEY (app_user_id) REFERENCES public.app_user(id);


--
-- Name: user_contact fk_user_contact_contact; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_contact
    ADD CONSTRAINT fk_user_contact_contact FOREIGN KEY (contact_app_user_id) REFERENCES public.app_user(id);


--
-- Name: user_contact fk_user_contact_owner; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_contact
    ADD CONSTRAINT fk_user_contact_owner FOREIGN KEY (owner_app_user_id) REFERENCES public.app_user(id);


--
-- Name: workflow_definition fk_workflow_def_creator; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_definition
    ADD CONSTRAINT fk_workflow_def_creator FOREIGN KEY (created_by_app_user_id) REFERENCES public.app_user(id);


--
-- Name: workflow_definition fk_workflow_def_org; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_definition
    ADD CONSTRAINT fk_workflow_def_org FOREIGN KEY (organization_id) REFERENCES public.organization(id);


--
-- Name: workflow_instance fk_workflow_inst_def; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_instance
    ADD CONSTRAINT fk_workflow_inst_def FOREIGN KEY (definition_id) REFERENCES public.workflow_definition(id);


--
-- Name: workflow_instance fk_workflow_inst_initiator; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_instance
    ADD CONSTRAINT fk_workflow_inst_initiator FOREIGN KEY (initiated_by_app_user_id) REFERENCES public.app_user(id);


--
-- Name: workflow_instance fk_workflow_inst_org; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_instance
    ADD CONSTRAINT fk_workflow_inst_org FOREIGN KEY (organization_id) REFERENCES public.organization(id);


--
-- Name: workflow_step_instance fk_workflow_step_inst; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_step_instance
    ADD CONSTRAINT fk_workflow_step_inst FOREIGN KEY (instance_id) REFERENCES public.workflow_instance(id);


--
-- Name: user_session fkactb4f15lwtv1hi46xpj72jce; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_session
    ADD CONSTRAINT fkactb4f15lwtv1hi46xpj72jce FOREIGN KEY (app_user_id) REFERENCES public.app_user(id);


--
-- Name: organization_subscription_policy fkby2nfrn5esslq300rjho0loxv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_subscription_policy
    ADD CONSTRAINT fkby2nfrn5esslq300rjho0loxv FOREIGN KEY (organization_id) REFERENCES public.organization(id);


--
-- Name: document fkcyvdrnad9256ggaitwgte3vu2; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document
    ADD CONSTRAINT fkcyvdrnad9256ggaitwgte3vu2 FOREIGN KEY (last_updated_by_id) REFERENCES public.app_user(id);


--
-- Name: organization_sharing_session_link fkevvh893aydrdluwjnea8x9uw0; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_sharing_session_link
    ADD CONSTRAINT fkevvh893aydrdluwjnea8x9uw0 FOREIGN KEY (requesting_organization_id) REFERENCES public.organization(id);


--
-- Name: app_user fkfl8n74ioc02b9cpjqdlpi0m9f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT fkfl8n74ioc02b9cpjqdlpi0m9f FOREIGN KEY (organization_id) REFERENCES public.organization(id);


--
-- Name: mfa_record fkgiceo3woeu8f7l4lt1i6bgrml; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mfa_record
    ADD CONSTRAINT fkgiceo3woeu8f7l4lt1i6bgrml FOREIGN KEY (app_user_id) REFERENCES public.app_user(id);


--
-- Name: person fkk542tk03xs8dlb4vdb4hd83op; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.person
    ADD CONSTRAINT fkk542tk03xs8dlb4vdb4hd83op FOREIGN KEY (contact_details_id) REFERENCES public.contact_details(id);


--
-- Name: audit_log fkkrscfnlpxlbsikdbou7hued6g; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT fkkrscfnlpxlbsikdbou7hued6g FOREIGN KEY (performed_by_app_user_id) REFERENCES public.app_user(id);


--
-- Name: sharing_session fkm047uybns2p1a6x8fhooegf3m; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sharing_session
    ADD CONSTRAINT fkm047uybns2p1a6x8fhooegf3m FOREIGN KEY (initiator_id) REFERENCES public.app_user(id);


--
-- Name: document_version fkmq8h439yekj9011u3q9e94acg; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_version
    ADD CONSTRAINT fkmq8h439yekj9011u3q9e94acg FOREIGN KEY (created_by) REFERENCES public.app_user(id);


--
-- Name: document_version fknvpdtplqabenasvgs0q5e3db4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_version
    ADD CONSTRAINT fknvpdtplqabenasvgs0q5e3db4 FOREIGN KEY (document_id) REFERENCES public.document(id);


--
-- Name: document_comment fko3016pqjk1g45uxgaksxgy85m; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_comment
    ADD CONSTRAINT fko3016pqjk1g45uxgaksxgy85m FOREIGN KEY (commented_by_user_id) REFERENCES public.app_user(id);


--
-- Name: organization_identity_provider_config fkoymxa0kwf903yr7br89s11uir; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_identity_provider_config
    ADD CONSTRAINT fkoymxa0kwf903yr7br89s11uir FOREIGN KEY (organization_id) REFERENCES public.organization(id);


--
-- Name: sharing_session_document fkpw6naen54xetoc37ebr8h946d; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sharing_session_document
    ADD CONSTRAINT fkpw6naen54xetoc37ebr8h946d FOREIGN KEY (documents_id) REFERENCES public.document(id);


--
-- Name: app_user fksbybenwfu7p3254px36jpl6iv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT fksbybenwfu7p3254px36jpl6iv FOREIGN KEY (settings_id) REFERENCES public.app_user_settings(id);


--
-- Name: organization_sharing_session_link fksdfhfouosmk7nc4xuho20wa1l; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_sharing_session_link
    ADD CONSTRAINT fksdfhfouosmk7nc4xuho20wa1l FOREIGN KEY (requested_organization_id) REFERENCES public.organization(id);


--
-- Name: contact_details fksr3dvnygknjf17useo3x0pjs6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contact_details
    ADD CONSTRAINT fksr3dvnygknjf17useo3x0pjs6 FOREIGN KEY (organization_id) REFERENCES public.organization(id);


--
-- Name: identity_provider_link fksu679qeqtmadtvehk08rcq2vb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.identity_provider_link
    ADD CONSTRAINT fksu679qeqtmadtvehk08rcq2vb FOREIGN KEY (app_user_id) REFERENCES public.app_user(id);


--
-- PostgreSQL database dump complete
--

