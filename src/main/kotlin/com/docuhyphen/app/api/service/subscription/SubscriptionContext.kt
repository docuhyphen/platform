package com.docuhyphen.app.api.service.subscription

import java.util.UUID

/**
 * Identifies the paying subject for an operation.
 *
 * Personal work resolves to the authenticated user. Work performed with an active organization
 * selected resolves to that organization. Operations against an existing resource must resolve
 * the context from the persisted owner of that resource rather than from a request header.
 */
data class SubscriptionContext(
    val ownerType: SubscriptionOwnerType,
    val ownerId: UUID,
)
{
    companion object
    {
        fun forUser(appUserId: UUID): SubscriptionContext
        {
            return SubscriptionContext(SubscriptionOwnerType.USER, appUserId)
        }

        fun forOrganization(organizationId: UUID): SubscriptionContext
        {
            return SubscriptionContext(SubscriptionOwnerType.ORGANIZATION, organizationId)
        }

        /**
         * Resolves the owner of a resource that stores either an owning user or an owning
         * organization. Organization ownership wins because an organization-owned resource is
         * paid for by the organization even when a member created it.
         */
        fun forOwner(ownerUserId: UUID?, ownerOrganizationId: UUID?): SubscriptionContext?
        {
            if (ownerOrganizationId != null)
            {
                return forOrganization(ownerOrganizationId)
            }

            if (ownerUserId != null)
            {
                return forUser(ownerUserId)
            }

            return null
        }
    }
}

