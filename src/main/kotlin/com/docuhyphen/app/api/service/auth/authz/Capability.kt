package com.docuhyphen.app.api.service.auth.authz

/**
 * Atomic permissions checked by [AuthorizationService]. Every [Action] maps to exactly
 * one required [Capability]; roles map to a set of capabilities in [RoleCapabilities].
 *
 * Resource type is conveyed through the ResourceRef so, for example, DOCUMENT_READ only
 * applies to a DOCUMENT ref and DOC_LIBRARY_READ only to a DOC_LIBRARY ref.
 *
 * Adding a new capability here grants it to no role automatically. A role must explicitly
 * list it in [RoleCapabilities] to receive it. This is the default-deny extension point
 * used by the Schema and Field capabilities.
 */
enum class Capability
{
    // Exchange lifecycle
    EXCHANGE_INITIATE,
    EXCHANGE_READ,
    EXCHANGE_WRITE,
    EXCHANGE_DELETE,
    EXCHANGE_ADMIN,
    EXCHANGE_OWNER,
    EXCHANGE_SHARE,
    EXCHANGE_RESCIND,

    // Exchange document
    DOCUMENT_READ,
    DOCUMENT_DOWNLOAD,
    DOCUMENT_WRITE,
    DOCUMENT_DELETE,
    DOCUMENT_COMMENT,
    DOCUMENT_SIGN,

    // Document Library
    DOC_LIBRARY_DISCOVER,
    DOC_LIBRARY_READ,
    DOC_LIBRARY_USE,
    DOC_LIBRARY_WRITE,
    DOC_LIBRARY_DELETE,
    DOC_LIBRARY_ADMIN,

    // Blueprint
    BLUEPRINT_DISCOVER,
    BLUEPRINT_READ,
    BLUEPRINT_USE,
    BLUEPRINT_WRITE,
    BLUEPRINT_DELETE,
    BLUEPRINT_CLONE,
    BLUEPRINT_PUBLISH,
    BLUEPRINT_ADMIN,

    // Workflow Definition
    WORKFLOW_DISCOVER,
    WORKFLOW_READ,
    WORKFLOW_USE,
    WORKFLOW_WRITE,
    WORKFLOW_DELETE,
    WORKFLOW_CLONE,
    WORKFLOW_PUBLISH,
    WORKFLOW_ADMIN,

    // Workflow Webhook
    WEBHOOK_ADMIN,
    WEBHOOK_DELIVER,
    WEBHOOK_AUDIT_READ,

    // Sequence
    SEQUENCE_DISCOVER,
    SEQUENCE_READ,
    SEQUENCE_CONSUME,
    SEQUENCE_WRITE,
    SEQUENCE_DELETE,
    SEQUENCE_ADMIN,

    // Variable
    VARIABLE_DISCOVER,
    VARIABLE_READ,
    VARIABLE_USE,
    VARIABLE_WRITE,
    VARIABLE_DELETE,
    VARIABLE_ADMIN,

    // Communication
    COMMUNICATION_DISCOVER,
    COMMUNICATION_READ,
    COMMUNICATION_USE,
    COMMUNICATION_WRITE,
    COMMUNICATION_DELETE,
    COMMUNICATION_PUBLISH,
    COMMUNICATION_ADMIN,

    // Principal Group
    GROUP_READ,
    GROUP_EDIT,
    GROUP_ADMIN,
    GROUP_DELETE,

    // Organization (administrative)
    ORG_MEMBER_MANAGE,
    ORG_POLICY_MANAGE,
    ORG_BILLING_MANAGE,
    ORG_AUDIT_READ,
    ORG_AUDIT_EXPORT,
    ORG_AUDIT_VIEW_SENSITIVE,

    // Fields & Schema configuration
    FIELD_SCHEMA_READ,
    FIELD_SCHEMA_WRITE,
    FIELD_SCHEMA_PUBLISH,

    // Platform
    APP_ADMIN,
    APP_AUDIT_READ,
    APP_AUDIT_EXPORT,
    APP_SUPPORT,

    // Application Registration
    APP_REG_READ,
    APP_REG_ADMIN,

    // Audit governance
    AUDIT_EXPORT_APPROVE,
    AUDIT_RETENTION_MANAGE,
    AUDIT_LEGAL_HOLD_MANAGE,
    AUDIT_INTEGRITY_VERIFY,
}
