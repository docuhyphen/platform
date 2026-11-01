package com.docuhyphen.app.api.model.subscription

import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import java.util.*

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

