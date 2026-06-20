package com.docuhyphen.app.api.service.auth.authz

/**
 * High-level operations callers ask `AuthorizationService.authorize(...)` about.
 * Each Action declares the [Capability] it requires. Add new actions here rather than
 * re-deriving the mapping at call sites, keeps the matrix in one place.
 */
enum class Action(val required: Capability)
{
    // Exchange
    EXCHANGE_VIEW(Capability.EXCHANGE_READ),
    EXCHANGE_EDIT(Capability.EXCHANGE_WRITE),
    EXCHANGE_DELETE(Capability.EXCHANGE_DELETE),
    EXCHANGE_SUSPEND(Capability.EXCHANGE_ADMIN),
    EXCHANGE_END(Capability.EXCHANGE_ADMIN),
    EXCHANGE_TRANSFER_OWNERSHIP(Capability.EXCHANGE_OWNER),
    EXCHANGE_MANAGE_ACCESS(Capability.EXCHANGE_SHARE),

    // Documents
    DOCUMENT_VIEW(Capability.DOCUMENT_READ),
    DOCUMENT_DOWNLOAD(Capability.DOCUMENT_DOWNLOAD),
    DOCUMENT_UPLOAD(Capability.DOCUMENT_WRITE),
    DOCUMENT_UPDATE(Capability.DOCUMENT_WRITE),
    DOCUMENT_DELETE(Capability.DOCUMENT_DELETE),
    DOCUMENT_COMMENT(Capability.DOCUMENT_COMMENT),
    DOCUMENT_SIGN(Capability.DOCUMENT_SIGN),

    // Groups
    GROUP_VIEW(Capability.GROUP_READ),
    GROUP_MANAGE_MEMBERS(Capability.GROUP_ADMIN),
    GROUP_DELETE(Capability.GROUP_DELETE),

    // Org
    ORG_MANAGE_MEMBERS(Capability.ORG_MEMBER_MANAGE),
    ORG_MANAGE_POLICY(Capability.ORG_POLICY_MANAGE),
    ORG_MANAGE_BILLING(Capability.ORG_BILLING_MANAGE),
    ORG_READ_AUDIT(Capability.ORG_AUDIT_READ),

    // App
    APP_ADMINISTRATE(Capability.APP_ADMIN),
    APP_READ_AUDIT(Capability.APP_AUDIT_READ),
}

