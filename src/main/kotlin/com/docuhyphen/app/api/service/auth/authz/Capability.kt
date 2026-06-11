package com.docuhyphen.app.api.service.auth.authz

/**
 * Atomic permissions checked by [AuthorizationService]. Every [Action] maps to exactly
 * one required [Capability]; roles map to a set of capabilities in [RoleCapabilities].
 *
 * Capabilities are intentionally coarse (read / write / delete / admin / share / sign)
 * rather than one capability per resource type. Resource type is conveyed through the
 * `ResourceRef`, so `DOCUMENT_READ` only applies to a `DOCUMENT` ref, etc.
 */
enum class Capability
{
    // Session-scoped
    EXCHANGE_READ,
    EXCHANGE_WRITE,
    EXCHANGE_DELETE,
    EXCHANGE_ADMIN,     // suspend / end / manage state
    EXCHANGE_OWNER,     // transfer ownership
    EXCHANGE_SHARE,     // add/remove recipients

    // Document-scoped
    DOCUMENT_READ,
    DOCUMENT_DOWNLOAD,
    DOCUMENT_WRITE,
    DOCUMENT_DELETE,
    DOCUMENT_COMMENT,
    DOCUMENT_SIGN,

    // Group-scoped
    GROUP_READ,
    GROUP_ADMIN,
    GROUP_DELETE,

    // Org-scoped (administrative)
    ORG_MEMBER_MANAGE,
    ORG_POLICY_MANAGE,
    ORG_BILLING_MANAGE,
    ORG_AUDIT_READ,

    // App-scoped
    APP_ADMIN,
    APP_AUDIT_READ,
    APP_SUPPORT,
}

