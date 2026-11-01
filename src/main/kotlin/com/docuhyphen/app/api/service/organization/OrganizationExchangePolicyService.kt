package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.model.entity.PrincipalGroupScope
import com.docuhyphen.app.api.service.auth.AuthAuditService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.*

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

        if (initiatorOrgId in recipientOrgIds) return

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
                    !organizationTrustExchangePolicyService.permitsExchange(initiatorOrgId, recipientOrgId)
                )
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
