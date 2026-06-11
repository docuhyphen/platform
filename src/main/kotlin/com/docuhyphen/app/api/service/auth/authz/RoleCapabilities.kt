package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.RoleName

/**
 * Static mapping from [RoleName] to the set of [Capability]s it grants. This is the
 * single source of truth for "what can role X do", no booleans scattered across entities.
 *
 * Resolution is most-permissive: when multiple roles apply, the union of their
 * capabilities wins. Explicit denies (from share constraints, org policy, session state)
 * are layered on top in `DefaultAuthorizationService`.
 */
object RoleCapabilities
{
    private val MAP: Map<RoleName, Set<Capability>> = mapOf(
        // --- System layer ---------------------------------------------------------
        RoleName.APP_ADMIN to enumValues<Capability>().toSet(),     // god mode, audited
        RoleName.APP_AUDITOR to setOf(
            Capability.APP_AUDIT_READ,
            Capability.ORG_AUDIT_READ,
            Capability.EXCHANGE_READ,
            Capability.DOCUMENT_READ,
            Capability.GROUP_READ,
        ),
        RoleName.APP_SUPPORT to setOf(
            Capability.APP_SUPPORT,
            Capability.EXCHANGE_READ,
            Capability.DOCUMENT_READ,
            Capability.GROUP_READ,
            Capability.ORG_AUDIT_READ,
        ),
        RoleName.END_USER to emptySet(),

        // --- Org layer ------------------------------------------------------------
        RoleName.ORG_OWNER to setOf(
            Capability.ORG_MEMBER_MANAGE,
            Capability.ORG_POLICY_MANAGE,
            Capability.ORG_BILLING_MANAGE,
            Capability.ORG_AUDIT_READ,
            Capability.GROUP_ADMIN,
            Capability.GROUP_READ,
            Capability.GROUP_DELETE,
        ),
        RoleName.ORG_ADMIN to setOf(
            Capability.ORG_MEMBER_MANAGE,
            Capability.ORG_POLICY_MANAGE,
            Capability.ORG_AUDIT_READ,
            Capability.GROUP_ADMIN,
            Capability.GROUP_READ,
            Capability.GROUP_DELETE,
        ),
        RoleName.ORG_BILLING_ADMIN to setOf(
            Capability.ORG_BILLING_MANAGE,
            Capability.ORG_AUDIT_READ,
        ),
        RoleName.ORG_USER_MANAGER to setOf(
            Capability.ORG_MEMBER_MANAGE,
            Capability.GROUP_READ,
        ),
        RoleName.ORG_AUDITOR to setOf(
            Capability.ORG_AUDIT_READ,
            Capability.EXCHANGE_READ,
            Capability.DOCUMENT_READ,
            Capability.GROUP_READ,
        ),
        RoleName.ORG_MEMBER to setOf(
            Capability.GROUP_READ,
        ),
        RoleName.ORG_GUEST to emptySet(),

        // --- Group layer ----------------------------------------------------------
        RoleName.OWNER to setOf(
            Capability.GROUP_READ,
            Capability.GROUP_ADMIN,
            Capability.GROUP_DELETE,
            Capability.EXCHANGE_OWNER,
            Capability.EXCHANGE_ADMIN,
            Capability.EXCHANGE_WRITE,
            Capability.EXCHANGE_READ,
            Capability.EXCHANGE_SHARE,
            Capability.EXCHANGE_DELETE,
            Capability.DOCUMENT_READ,
            Capability.DOCUMENT_DOWNLOAD,
            Capability.DOCUMENT_WRITE,
            Capability.DOCUMENT_DELETE,
            Capability.DOCUMENT_COMMENT,
        ),
        RoleName.MANAGER to setOf(
            Capability.GROUP_READ,
            Capability.GROUP_ADMIN,
            Capability.EXCHANGE_READ,
            Capability.EXCHANGE_WRITE,
            Capability.EXCHANGE_ADMIN,
            Capability.EXCHANGE_SHARE,
            Capability.DOCUMENT_READ,
            Capability.DOCUMENT_DOWNLOAD,
            Capability.DOCUMENT_WRITE,
            Capability.DOCUMENT_COMMENT,
        ),
        RoleName.MEMBER to setOf(
            Capability.GROUP_READ,
            Capability.EXCHANGE_READ,
            Capability.DOCUMENT_READ,
            Capability.DOCUMENT_DOWNLOAD,
            Capability.DOCUMENT_COMMENT,
        ),
        RoleName.OBSERVER to setOf(
            Capability.GROUP_READ,
            Capability.EXCHANGE_READ,
            Capability.DOCUMENT_READ,
        ),

        // --- Resource layer (on a Share) ------------------------------------------
        RoleName.EDITOR to setOf(
            Capability.EXCHANGE_READ,
            Capability.EXCHANGE_WRITE,
            Capability.DOCUMENT_READ,
            Capability.DOCUMENT_DOWNLOAD,
            Capability.DOCUMENT_WRITE,
            Capability.DOCUMENT_COMMENT,
        ),
        RoleName.REVIEWER to setOf(
            Capability.EXCHANGE_READ,
            Capability.DOCUMENT_READ,
            Capability.DOCUMENT_DOWNLOAD,
            Capability.DOCUMENT_COMMENT,
        ),
        RoleName.SIGNER to setOf(
            Capability.EXCHANGE_READ,
            Capability.DOCUMENT_READ,
            Capability.DOCUMENT_DOWNLOAD,
            Capability.DOCUMENT_SIGN,
        ),
        RoleName.VIEWER to setOf(
            Capability.EXCHANGE_READ,
            Capability.DOCUMENT_READ,
            // DOCUMENT_DOWNLOAD is conditional on share constraints.can_download, applied
            // dynamically by DefaultAuthorizationService.
        ),
        RoleName.COMMENTER to setOf(
            Capability.EXCHANGE_READ,
            Capability.DOCUMENT_READ,
            Capability.DOCUMENT_COMMENT,
        ),
        RoleName.PARTICIPANT to setOf(
            Capability.EXCHANGE_READ,
            Capability.DOCUMENT_READ,
        ),
    )

    fun forRole(role: RoleName): Set<Capability> = MAP[role] ?: emptySet()

    fun forRole(roleName: String): Set<Capability> =
        runCatching { RoleName.valueOf(roleName) }.getOrNull()?.let(::forRole) ?: emptySet()
}

