package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.interceptor.AuthTokenContext
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
 *   * **B2B, recipient belongs to another organization.** Allowed only when the two orgs have an
 *     ACCEPTED pairing link ([OrganizationExchangeLink], either direction), or the initiator
 *     org has explicitly set [OrganizationSettings.allowShareWithoutPairing] = `true`.
 *
 * Initiators who belong to no organization are unconstrained, the policy is an org-level control
 * and there is no org to read the settings from.
 */
@ApplicationScoped
class OrganizationExchangePolicyService @Inject constructor(
    private val organizationService: OrganizationService,
    private val authTokenContext: AuthTokenContext,
    private val organizationMembershipService: OrganizationMembershipService,
    private val organizationExchangeLinkService: OrganizationExchangeLinkService,
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
    fun assertCanShareWithUser(initiatorAppUserId: UUID, recipientAppUserId: UUID?)
    {
        val initiatorOrgId = authTokenContext.activeOrganizationId
            ?: return

        val organization = organizationService.getOrganizationById(initiatorOrgId)
        val settings = organization.settings

        val recipientOrgId = recipientAppUserId
            ?.let { organizationMembershipService.primaryOrganizationId(it) }

        // Internal share (same org) is always allowed.
        if (recipientOrgId != null && recipientOrgId == initiatorOrgId) return

        // B2C, recipient is an external individual with no org. Allowed by default; an org may
        // opt out via allowExternalCustomerSharing. Audit-logged because it bypasses the pairing
        // gate, so admins retain visibility into external-customer shares.
        if (recipientOrgId == null)
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
        val allowShareWithoutPairing = settings?.allowShareWithoutPairing ?: false
        if (allowShareWithoutPairing) return
        if (arePaired(initiatorOrgId, recipientOrgId)) return

        throw IllegalArgumentException(
            "Your organization only permits sharing with members of your organization or a paired organization."
        )
    }

    fun assertCanShareWithGroup(initiatorAppUserId: UUID, group: PrincipalGroup)
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
                val initiatorOrgId = authTokenContext.activeOrganizationId
                    ?: throw IllegalArgumentException("An organization is required to share with an organization group")
                val recipientOrgId = group.ownerOrganizationId
                    ?: throw IllegalArgumentException("The selected group has no owning organization")
                if (recipientOrgId == initiatorOrgId) return
                if (!group.externallyPublished)
                {
                    throw IllegalArgumentException("The selected group is not available for external sharing")
                }

                if (!arePaired(initiatorOrgId, recipientOrgId))
                {
                    throw IllegalArgumentException("Your organization only permits sharing with groups from a paired organization")
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

    private fun arePaired(orgA: UUID, orgB: UUID): Boolean
    {
        return organizationExchangeLinkService.hasAcceptedLink(orgA, orgB)
    }
}
