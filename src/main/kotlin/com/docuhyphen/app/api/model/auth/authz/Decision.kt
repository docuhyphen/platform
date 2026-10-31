package com.docuhyphen.app.api.model.auth.authz

import com.docuhyphen.app.api.service.auth.authz.Capability
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import java.util.*

sealed class Decision
{
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
        const val REASON_NO_GRANT = "NO_GRANT"
        const val REASON_SHARE_EXPIRED = "SHARE_EXPIRED"
        const val REASON_SHARE_NOT_ACTIVE = "SHARE_NOT_ACTIVE"
        const val REASON_MFA_REQUIRED = "MFA_REQUIRED"
        const val REASON_IP_DENIED = "IP_DENIED"
        const val REASON_INVALID_CONSTRAINTS = "INVALID_CONSTRAINTS"
        const val REASON_EXCHANGE_SUSPENDED = "EXCHANGE_SUSPENDED"
        const val REASON_EXCHANGE_ARCHIVED = "EXCHANGE_ARCHIVED"
        const val REASON_POLICY_BLOCKED = "POLICY_BLOCKED"
        const val REASON_LINK_EXHAUSTED = "LINK_EXHAUSTED"
        const val REASON_LINK_DOMAIN = "LINK_DOMAIN_DENIED"

        const val REASON_RESOURCE_CONTEXT_UNRESOLVED = "RESOURCE_CONTEXT_UNRESOLVED"

        const val REASON_PARENT_CONTEXT_UNRESOLVED = "PARENT_CONTEXT_UNRESOLVED"

        const val REASON_PARENT_OWNER_MISMATCH = "PARENT_OWNER_MISMATCH"

        const val REASON_PARENT_INHERITANCE_CYCLE = "PARENT_INHERITANCE_CYCLE"

        const val REASON_RESOURCE_POLICY_FACTS_UNAVAILABLE = "RESOURCE_POLICY_FACTS_UNAVAILABLE"
    }
}

data class Grant(
    val sourceKind: SourceKind,
    val sourceId: UUID,
    val roleName: String,
    val capabilities: Set<Capability>,
    val expiresAtEpochMillis: Long? = null,
    val inheritedFrom: ResourceRef? = null,
)
{
    enum class SourceKind
    {
        ROLE_ASSIGNMENT,

        APPLICATION_ROLE,

        ORG_MEMBERSHIP,

        GROUP_MEMBERSHIP,
        DIRECT_SHARE,
        INHERITED_GROUP_SHARE,
        INHERITED_ORG_SHARE,
        SHARE_LINK,
        DELEGATED_AUTHORITY,
    }
}

data class ShareObligations(
    val watermark: Boolean = false,
    val allowedDownloadFormats: Set<String>? = null,
)
