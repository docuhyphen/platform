package com.docuhyphen.app.api.service.audit.catalog

/**
 * Canonical, versioned catalog of audit event types.
 *
 * Every entry has a stable, namespaced [key] (e.g. `auth.sign_in.completion`,
 * `document.download`) that is safe to persist, index, and rely on across releases. The enum
 * constant name mirrors the authentication action string emitted by [AuthAuditService]
 * or [DocumentAuditAction] so capture sites can be mapped onto the catalog without
 * guessing, but the [key] is what gets persisted and read going forward.
 *
 * This catalog contains the event types emitted by authentication, document, administration,
 * Exchange, and governance services. Owning services use these stable keys when recording events.
 */
enum class AuditEventType(val key: String, val category: AuditCategory)
{
    // Authentication / session lifecycle.
    REQUEST_AUTH("auth.session.request_auth", AuditCategory.AUTHENTICATION),
    SIGN_IN_INITIATE("auth.sign_in.initiate", AuditCategory.AUTHENTICATION),
    SIGN_IN_LOOKUP("auth.sign_in.lookup", AuditCategory.AUTHENTICATION),
    SIGN_IN_COMPLETION("auth.sign_in.completion", AuditCategory.AUTHENTICATION),
    SIGN_OUT("auth.sign_out", AuditCategory.AUTHENTICATION),
    TOKEN_REFRESH("auth.token.refresh", AuditCategory.AUTHENTICATION),
    STEP_UP_INITIATE("auth.step_up.initiate", AuditCategory.AUTHENTICATION),
    STEP_UP_COMPLETE("auth.step_up.complete", AuditCategory.AUTHENTICATION),
    STEP_UP_OAUTH_CALLBACK("auth.step_up.oauth_callback", AuditCategory.AUTHENTICATION),
    STEP_UP_REGENERATE_OTP("auth.step_up.otp_regenerate", AuditCategory.AUTHENTICATION),
    OAUTH_AUTHORIZE("auth.oauth.authorize", AuditCategory.AUTHENTICATION),
    OAUTH_CALLBACK("auth.oauth.callback", AuditCategory.AUTHENTICATION),
    OAUTH_LINK_CONFIRM("auth.oauth.link_confirm", AuditCategory.AUTHENTICATION),
    SESSION_DELETE_RECORD("auth.session.delete_record", AuditCategory.AUTHENTICATION),
    DIRECTORY_LOOKUP("auth.directory.lookup", AuditCategory.AUTHENTICATION),
    RECIPIENT_RESOLVE("auth.recipient.resolve", AuditCategory.AUTHENTICATION),

    // Administration.
    ADMIN_APPROVAL_INITIATE("admin.approval.initiate", AuditCategory.ADMINISTRATION),
    ADMIN_APPROVAL_APPROVE("admin.approval.approve", AuditCategory.ADMINISTRATION),

    // Security.
    SECURITY_INCIDENT("security.incident.raised", AuditCategory.SECURITY),

    // Organization.
    ORG_UPDATE("organization.update", AuditCategory.ORGANIZATION),
    ORG_APP_USER_ADD("organization.app_user.add", AuditCategory.ORGANIZATION),
    ORG_APP_USER_UPDATE("organization.app_user.update", AuditCategory.ORGANIZATION),
    ORG_APP_USER_DELETE("organization.app_user.delete", AuditCategory.ORGANIZATION),
    ORG_GROUP_ADD("organization.group.add", AuditCategory.ORGANIZATION),
    ORG_GROUP_UPDATE("organization.group.update", AuditCategory.ORGANIZATION),
    ORG_GROUP_DELETE("organization.group.delete", AuditCategory.ORGANIZATION),
    ORG_TRUST_REQUESTED("organization.trust.requested", AuditCategory.ORGANIZATION),
    ORG_TRUST_REREQUESTED("organization.trust.rerequested", AuditCategory.ORGANIZATION),
    ORG_TRUST_ACCEPTED("organization.trust.accepted", AuditCategory.ORGANIZATION),
    ORG_TRUST_REJECTED("organization.trust.rejected", AuditCategory.ORGANIZATION),
    ORG_TRUST_WITHDRAWN("organization.trust.withdrawn", AuditCategory.ORGANIZATION),
    ORG_TRUST_EXPIRED("organization.trust.expired", AuditCategory.ORGANIZATION),
    ORG_TRUST_SUSPENDED("organization.trust.suspended", AuditCategory.ORGANIZATION),
    ORG_TRUST_RESUMED("organization.trust.resumed", AuditCategory.ORGANIZATION),
    ORG_TRUST_ENDED("organization.trust.ended", AuditCategory.ORGANIZATION),
    ORG_TRUST_POLICY_UPDATED("organization.trust.policy.updated", AuditCategory.ORGANIZATION),
    ORG_TRUST_PUBLISHED_GROUP_LISTED("organization.trust.published_group.listed", AuditCategory.ORGANIZATION),
    ORG_TRUST_PUBLISHED_GROUP_DENIED("organization.trust.published_group.denied", AuditCategory.ORGANIZATION),
    ORG_TRUST_IDENTITY_RESOLUTION_ALLOWED("organization.trust.identity_resolution.allowed", AuditCategory.ORGANIZATION),
    ORG_TRUST_IDENTITY_RESOLUTION_DENIED("organization.trust.identity_resolution.denied", AuditCategory.ORGANIZATION),
    ORG_TRUST_IDENTITY_RESOLUTION_EXPIRED("organization.trust.identity_resolution.expired", AuditCategory.ORGANIZATION),
    ORG_TRUST_IDENTITY_RESOLUTION_CONSUMED("organization.trust.identity_resolution.consumed", AuditCategory.ORGANIZATION),
    ORG_TRUST_IDENTITY_RESOLUTION_REPLAY_DENIED("organization.trust.identity_resolution.replay_denied", AuditCategory.ORGANIZATION),
    ORG_TRUST_RECIPIENT_VALIDATION_ALLOWED("organization.trust.recipient_validation.allowed", AuditCategory.ORGANIZATION),
    ORG_TRUST_RECIPIENT_VALIDATION_DENIED("organization.trust.recipient_validation.denied", AuditCategory.ORGANIZATION),
    ORG_TRUST_ACCEPTANCE_ALLOWED("organization.trust.acceptance.allowed", AuditCategory.ORGANIZATION),
    ORG_TRUST_ACCEPTANCE_DENIED("organization.trust.acceptance.denied", AuditCategory.ORGANIZATION),
    ORG_SHARE_EXTERNAL_CUSTOMER("organization.share.external_customer", AuditCategory.ORGANIZATION),
    ORG_AUTH_EXCHANGE_POLICY_UPDATE("organization.auth_exchange_policy.update", AuditCategory.ORGANIZATION),
    ORG_AUTH_EXCHANGE_POLICY_VIEW("organization.auth_exchange_policy.view", AuditCategory.ORGANIZATION),

    ORG_IDP_CONFIG_CREATE("organization.idp_config.create", AuditCategory.IDENTITY_PROVIDER),
    ORG_IDP_CONFIG_UPDATE("organization.idp_config.update", AuditCategory.IDENTITY_PROVIDER),
    ORG_IDP_CONFIG_DELETE("organization.idp_config.delete", AuditCategory.IDENTITY_PROVIDER),
    ORG_IDP_CONFIG_LIST("organization.idp_config.list", AuditCategory.IDENTITY_PROVIDER),
    ORG_IDP_SECRET_ACTIVATE("organization.idp_secret.activate", AuditCategory.IDENTITY_PROVIDER),
    ORG_IDP_SECRET_DISABLE("organization.idp_secret.disable", AuditCategory.IDENTITY_PROVIDER),
    ORG_IDP_SECRET_ENABLE("organization.idp_secret.enable", AuditCategory.IDENTITY_PROVIDER),
    ORG_IDP_SECRET_RETIRE("organization.idp_secret.retire", AuditCategory.IDENTITY_PROVIDER),
    ORG_IDP_SECRET_ROLLBACK("organization.idp_secret.rollback", AuditCategory.IDENTITY_PROVIDER),
    ORG_IDP_SECRET_ROTATE("organization.idp_secret.rotate", AuditCategory.IDENTITY_PROVIDER),

    PLATFORM_ORG_SUBSCRIPTION_POLICY_DELETE("platform.org_subscription_policy.delete", AuditCategory.PLATFORM),
    PLATFORM_ORG_SUBSCRIPTION_POLICY_LIST("platform.org_subscription_policy.list", AuditCategory.PLATFORM),
    PLATFORM_ORG_SUBSCRIPTION_POLICY_UPSERT("platform.org_subscription_policy.upsert", AuditCategory.PLATFORM),
    PLATFORM_ORG_SUBSCRIPTION_POLICY_VIEW("platform.org_subscription_policy.view", AuditCategory.PLATFORM),

    SCIM_USER_CREATE("scim.user.create", AuditCategory.SCIM),
    SCIM_USER_DEPROVISION("scim.user.deprovision", AuditCategory.SCIM),
    SCIM_USER_PATCH("scim.user.patch", AuditCategory.SCIM),
    SCIM_USER_REPLACE("scim.user.replace", AuditCategory.SCIM),

    DOCUMENT_UPLOAD("document.upload", AuditCategory.DOCUMENT),
    DOCUMENT_DOWNLOAD("document.download", AuditCategory.DOCUMENT),
    DOCUMENT_VIEW("document.view", AuditCategory.DOCUMENT),
    DOCUMENT_CREATED("document.created", AuditCategory.DOCUMENT),
    DOCUMENT_DELETE("document.delete", AuditCategory.DOCUMENT),
    DOCUMENT_UPDATE("document.update", AuditCategory.DOCUMENT),
    DOCUMENT_COMMENT("document.comment", AuditCategory.DOCUMENT),
    DOCUMENT_VERSION_CREATED("document.version_created", AuditCategory.DOCUMENT),

    EXCHANGE_RESCINDED("exchange.lifecycle.rescinded", AuditCategory.EXCHANGE),
    EXCHANGE_REVOKE("exchange.lifecycle.revoke", AuditCategory.EXCHANGE),

    ORG_IDP_SECRET_ROTATION_JOB("organization.idp_secret.rotation_job", AuditCategory.IDENTITY_PROVIDER),
    ORG_IDP_SECRET_ROTATION_RUNBOOK("organization.idp_secret.rotation_runbook", AuditCategory.IDENTITY_PROVIDER),
    WEBHOOK_DELIVERY_FAILED("application.webhook.delivery_failed", AuditCategory.ADMINISTRATION),

    DOCUMENT_PREVIEW("document.preview", AuditCategory.DOCUMENT),
    DOCUMENT_VERSION_DOWNLOAD("document.version_download", AuditCategory.DOCUMENT),
    DOCUMENT_ZIP_EXPORT("document.zip_export", AuditCategory.DOCUMENT),
    DOCUMENT_LIBRARY_DOWNLOAD("document.library_download", AuditCategory.DOCUMENT),
    DOCUMENT_NO_AUTH_DOWNLOAD("document.no_auth_download", AuditCategory.DOCUMENT),

    SHARE_GRANT("authorization.share.grant", AuditCategory.AUTHORIZATION),
    SHARE_ACTIVATE("authorization.share.activate", AuditCategory.AUTHORIZATION),
    SHARE_ROLE_CHANGE("authorization.share.role_change", AuditCategory.AUTHORIZATION),
    SHARE_REVOKE("authorization.share.revoke", AuditCategory.AUTHORIZATION),
    AUTHORIZATION_DENIED("authorization.decision.denied", AuditCategory.AUTHORIZATION),

    // Additional Exchange lifecycle transitions beyond EXCHANGE_RESCINDED.
    EXCHANGE_ACCEPTED("exchange.lifecycle.accepted", AuditCategory.EXCHANGE),
    EXCHANGE_REJECTED("exchange.lifecycle.rejected", AuditCategory.EXCHANGE),
    EXCHANGE_ENDED("exchange.lifecycle.ended", AuditCategory.EXCHANGE),
    EXCHANGE_DELETED("exchange.lifecycle.deleted", AuditCategory.EXCHANGE),

    // Workflow definition mutations with the highest audit value: create, update, and
    // delete/publish; instance start/step decisions already flow through
    // WorkflowEngineService.recordDecision's own concerns and are deferred, see handoff).
    WORKFLOW_DEFINITION_CREATE("workflow.definition.create", AuditCategory.WORKFLOW),
    WORKFLOW_DEFINITION_UPDATE("workflow.definition.update", AuditCategory.WORKFLOW),
    WORKFLOW_DEFINITION_DELETE("workflow.definition.delete", AuditCategory.WORKFLOW),
    WORKFLOW_DEFINITION_PUBLISH("workflow.definition.publish", AuditCategory.WORKFLOW),

    // Field and schema mutations.
    FIELD_DEFINITION_CREATE("field_schema.field_definition.create", AuditCategory.FIELD_SCHEMA),
    FIELD_DEFINITION_RETIRE("field_schema.field_definition.retire", AuditCategory.FIELD_SCHEMA),
    SCHEMA_DEFINITION_CREATE("field_schema.schema_definition.create", AuditCategory.FIELD_SCHEMA),
    SCHEMA_DEFINITION_PUBLISH("field_schema.schema_definition.publish", AuditCategory.FIELD_SCHEMA),
    SCHEMA_DEFINITION_RETIRE("field_schema.schema_definition.retire", AuditCategory.FIELD_SCHEMA),

    // Organization membership and role changes.
    ORG_MEMBERSHIP_ROLE_ASSIGN("organization.membership.role_assign", AuditCategory.ORGANIZATION),
    ORG_MEMBERSHIP_ROLE_REMOVE("organization.membership.role_remove", AuditCategory.ORGANIZATION),
    ORG_MEMBERSHIP_REMOVE("organization.membership.remove", AuditCategory.ORGANIZATION),

    ARCHIVE_SEGMENT_CLOSED("archive.segment.closed", AuditCategory.ARCHIVE),
    ARCHIVE_INTEGRITY_VERIFIED("archive.integrity.verified", AuditCategory.ARCHIVE),
    ARCHIVE_INTEGRITY_FAILED("archive.integrity.failed", AuditCategory.ARCHIVE),


    AUDIT_ENGAGEMENT_REQUESTED("audit.engagement.requested", AuditCategory.AUDIT_GOVERNANCE),
    AUDIT_ENGAGEMENT_APPROVED("audit.engagement.approved", AuditCategory.AUDIT_GOVERNANCE),
    AUDIT_ENGAGEMENT_REVOKED("audit.engagement.revoked", AuditCategory.AUDIT_GOVERNANCE),
    AUDIT_ENGAGEMENT_EXPIRED("audit.engagement.expired", AuditCategory.AUDIT_GOVERNANCE),
    AUDIT_SEARCH_PERFORMED("audit.search.performed", AuditCategory.AUDIT_GOVERNANCE),
    AUDIT_EVENT_VIEWED("audit.event.viewed", AuditCategory.AUDIT_GOVERNANCE),
    AUDIT_ACCESS_DENIED("audit.access.denied", AuditCategory.AUDIT_GOVERNANCE),

    AUDIT_EXPORT_REQUESTED("audit.export.requested", AuditCategory.AUDIT_GOVERNANCE),
    AUDIT_EXPORT_APPROVED("audit.export.approved", AuditCategory.AUDIT_GOVERNANCE),
    AUDIT_EXPORT_BUILDING("audit.export.building", AuditCategory.AUDIT_GOVERNANCE),
    AUDIT_EXPORT_READY("audit.export.ready", AuditCategory.AUDIT_GOVERNANCE),
    AUDIT_EXPORT_FAILED("audit.export.failed", AuditCategory.AUDIT_GOVERNANCE),
    AUDIT_EXPORT_DOWNLOADED("audit.export.downloaded", AuditCategory.AUDIT_GOVERNANCE),
    AUDIT_EXPORT_DOWNLOAD_INTEGRITY_FAILED("audit.export.download_integrity_failed", AuditCategory.AUDIT_GOVERNANCE),
    AUDIT_EXPORT_EXPIRED("audit.export.expired", AuditCategory.AUDIT_GOVERNANCE),
    AUDIT_EXPORT_REVOKED("audit.export.revoked", AuditCategory.AUDIT_GOVERNANCE),
    AUDIT_INTEGRITY_CHECK_PERFORMED("audit.integrity.check_performed", AuditCategory.AUDIT_GOVERNANCE),

    AUDIT_RETENTION_POLICY_UPDATED("audit.retention_policy.updated", AuditCategory.AUDIT_GOVERNANCE),
    AUDIT_LEGAL_HOLD_PLACED("audit.legal_hold.placed", AuditCategory.AUDIT_GOVERNANCE),
    AUDIT_LEGAL_HOLD_RELEASED("audit.legal_hold.released", AuditCategory.AUDIT_GOVERNANCE),
    AUDIT_IDENTITY_KEY_SHREDDED("audit.identity_key.shredded", AuditCategory.AUDIT_GOVERNANCE),
    AUDIT_ANALYTICS_RECONCILED("audit.analytics.reconciled", AuditCategory.AUDIT_GOVERNANCE),
    ;

    companion object
    {
        /**
         * Bumped whenever event types are added, removed, or reinterpreted so downstream
         * consumers (ledger, exports, projections) can reason about which catalog shape produced
         * a given event.
         */
        const val CATALOG_VERSION: Int = 11

        private val byKey: Map<String, AuditEventType> = entries.associateBy { it.key }

        fun findByKey(key: String): AuditEventType? = byKey[key]
    }
}
