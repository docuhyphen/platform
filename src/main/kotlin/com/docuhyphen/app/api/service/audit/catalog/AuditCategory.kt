package com.docuhyphen.app.api.service.audit.catalog

/**
 * High-level grouping for [AuditEventType] entries. Used for coarse filtering (auditor portal,
 * failure-policy routing) without exposing the full namespaced event key.
 */
enum class AuditCategory
{
    AUTHENTICATION,
    ADMINISTRATION,
    SECURITY,
    ORGANIZATION,
    IDENTITY_PROVIDER,
    PLATFORM,
    SCIM,
    DOCUMENT,
    EXCHANGE,
    AUTHORIZATION,
    WORKFLOW,
    FIELD_SCHEMA,
    ARCHIVE,
    AUDIT_GOVERNANCE,
}
