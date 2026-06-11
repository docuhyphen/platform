package com.docuhyphen.app.api.service.auth.authz

import java.util.UUID

/** Result of an authorisation check. */
sealed class Decision
{
    /**
     * Non-blocking duties the caller must honour when serving the resource (watermark the
     * render, cap the view count, …). Carried on both outcomes so a future "deny but tell
     * me why" path can attach them too; today only [Allow] populates them.
     */
    abstract val obligations: ShareObligations

    data class Allow(override val obligations: ShareObligations = ShareObligations()) : Decision()
    data class Deny(
        val reasonCode: String,
        val message: String,
        override val obligations: ShareObligations = ShareObligations(),
    ) : Decision()

    val isAllowed: Boolean get() = this is Allow

    companion object
    {
        const val REASON_NO_GRANT          = "NO_GRANT"
        const val REASON_SHARE_EXPIRED     = "SHARE_EXPIRED"
        const val REASON_SHARE_NOT_ACTIVE  = "SHARE_NOT_ACTIVE"
        const val REASON_MFA_REQUIRED      = "MFA_REQUIRED"
        const val REASON_IP_DENIED         = "IP_DENIED"
        const val REASON_EXCHANGE_SUSPENDED = "EXCHANGE_SUSPENDED"
        const val REASON_EXCHANGE_ARCHIVED  = "EXCHANGE_ARCHIVED"
        const val REASON_POLICY_BLOCKED    = "POLICY_BLOCKED"
        const val REASON_LINK_EXHAUSTED    = "LINK_EXHAUSTED"
        const val REASON_LINK_DOMAIN       = "LINK_DOMAIN_DENIED"
    }
}

/**
 * One contributing grant in an authorisation decision. Returned by
 * [AuthorizationService.grantsOn] for diagnostics, audit, and the "Manage Access" UI.
 */
data class Grant(
    val sourceKind: SourceKind,
    val sourceId: UUID,
    val roleName: String,
    val capabilities: Set<Capability>,
    val expiresAtEpochMillis: Long? = null,
)
{
    enum class SourceKind
    {
        ROLE_ASSIGNMENT,
        /** Caller's role on an [com.docuhyphen.app.api.model.entity.OrganizationMembership]. */
        ORG_MEMBERSHIP,
        /** Caller's [com.docuhyphen.app.api.model.entity.GroupRole] within the target group. */
        GROUP_MEMBERSHIP,
        DIRECT_SHARE,
        INHERITED_GROUP_SHARE,
        INHERITED_ORG_SHARE,
        SHARE_LINK,
    }
}

/**
 * Non-blocking duties attached to an allowed [Decision]. Derived from the most-restrictive
 * union of the applicable shares' constraints (any watermark wins; the smallest max_views
 * wins). The render / download path is responsible for honouring them.
 */
data class ShareObligations(
    val watermark: Boolean = false,
    val maxViews: Int? = null,
)

