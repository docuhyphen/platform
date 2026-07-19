package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.model.entity.PrincipalGroupScope
import com.docuhyphen.app.api.service.auth.AuthAuditService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Enforces an organization's outbound-sharing policy on the share-initiation path. The policy
 * distinguishes by the **nature of the recipient**, because sharing to a person is not
 * the same act as federating into another managed tenant:
 *
 *   * **B2C, recipient is an individual with no organization.** Allowed by default. Gated only by
 *     [OrganizationSettings.allowExternalCustomerSharing] (**default `true`**); an org may opt out.
 *     This is the headline B2C topology and must be ergonomic out of the box.
 *   * **Internal, recipient is in the initiator's own org.** Always allowed.
 *   * **B2B, recipient belongs to another organization.** When the initiator org has
 *     [OrganizationSettings.requireTrustedOrganizationForB2b] set (**default `true`**), the share is
 *     allowed only when both organizations, the relationship, and both directional policies are
 *     currently eligible. When the requirement is disabled, B2B sharing is unrestricted.
 *
 * The caller supplies its validated active organization explicitly. When it is null the initiator
 * belongs to no organization for this action, the policy is an org-level control and there is no org
 * to read the settings from, so the share is unconstrained.
 */
@ApplicationScoped
class OrganizationExchangePolicyService @Inject constructor(
    private val organizationService: OrganizationService,
    private val organizationMembershipService: OrganizationMembershipService,
    private val organizationTrustExchangePolicyService: OrganizationTrustExchangePolicyService,
    private val authAuditService: AuthAuditService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationExchangePolicyService::class.java)
    }

    /**
     * Throws [IllegalArgumentException] when the initiator's org policy forbids the share.
     * [recipientAppUserId] is null for recipients with no resolvable account (e.g. a brand-new
     * external email), treated as an external individual (B2C).
     */
    fun assertCanShareWithUser(
        callerOrganizationId: UUID?,
        initiatorAppUserId: UUID,
        recipientAppUserId: UUID?,
    )
    {
        val initiatorOrgId = callerOrganizationId
            ?: return

        val organization = organizationService.getOrganizationById(initiatorOrgId)
        val settings = organization.settings

        val recipientOrgIds = recipientAppUserId
            ?.let { organizationMembershipService.activeOrganizationIds(it) }
            ?: emptySet()

        // Internal share (same org) is always allowed.
        if (initiatorOrgId in recipientOrgIds) return

        // B2C, recipient is an external individual with no org. Allowed by default; an org may
        // opt out via allowExternalCustomerSharing. Audit-logged because it bypasses the B2B trust
        // gate, so admins retain visibility into external-customer shares.
        if (recipientOrgIds.isEmpty())
        {
            val allowExternalCustomerSharing = settings?.allowExternalCustomerSharing ?: true
            if (!allowExternalCustomerSharing)
            {
                throw IllegalArgumentException(
                    "Your organization does not permit sharing with external individual customers."
                )
            }
            auditExternalCustomerShare(initiatorAppUserId, initiatorOrgId, recipientAppUserId)
            return
        }

        // B2B, recipient belongs to another organization.
        val requireTrustedOrganizationForB2b = settings?.requireTrustedOrganizationForB2b ?: true
        if (!requireTrustedOrganizationForB2b) return
        if (recipientOrgIds.any {
                organizationTrustExchangePolicyService.permitsExchange(initiatorOrgId, it)
            })
        {
            return
        }

        throw IllegalArgumentException(
            "Your organization only permits sharing with members of your organization or a trusted organization."
        )
    }

    fun assertCanShareWithGroup(
        callerOrganizationId: UUID?,
        initiatorAppUserId: UUID,
        group: PrincipalGroup,
    )
    {
        if (!group.isActive) throw IllegalArgumentException("The selected group is inactive")

        when (group.scope)
        {
            PrincipalGroupScope.PERSONAL ->
            {
                if (group.ownerAppUserId != initiatorAppUserId)
                {
                    throw IllegalArgumentException("You can only share with a personal group that you own")
                }
            }

            PrincipalGroupScope.ORG ->
            {
                val initiatorOrgId = callerOrganizationId
                    ?: throw IllegalArgumentException("An organization is required to share with an organization group")
                val recipientOrgId = group.ownerOrganizationId
                    ?: throw IllegalArgumentException("The selected group has no owning organization")
                if (recipientOrgId == initiatorOrgId) return
                if (!group.externallyPublished)
                {
                    throw IllegalArgumentException("The selected group is not available for external sharing")
                }

                val organization = organizationService.getOrganizationById(initiatorOrgId)
                val requireTrustedOrganizationForB2b =
                    organization.settings?.requireTrustedOrganizationForB2b ?: true
                if (requireTrustedOrganizationForB2b &&
                    !organizationTrustExchangePolicyService.permitsExchange(initiatorOrgId, recipientOrgId))
                {
                    throw IllegalArgumentException("Your organization only permits sharing with groups from a trusted organization")
                }
            }

            PrincipalGroupScope.SHARED_PROJECT ->
                throw IllegalArgumentException("Shared project groups cannot be used as Exchange access principals")
        }
    }

    private fun auditExternalCustomerShare(initiatorAppUserId: UUID, initiatorOrgId: UUID, recipientAppUserId: UUID?)
    {
        try
        {
            authAuditService.emit(
                action = "ORG_SHARE_EXTERNAL_CUSTOMER",
                outcome = "ALLOWED",
                actorId = initiatorAppUserId,
                organizationId = initiatorOrgId,
                targetType = "APP_USER",
                targetId = recipientAppUserId?.toString(),
                reason = "Org shared with an external individual customer (no organization).",
            )
        }
        catch (exception: Exception)
        {
            logger.warn("Failed to audit external-customer share for org {}", initiatorOrgId, exception)
        }
    }

}
