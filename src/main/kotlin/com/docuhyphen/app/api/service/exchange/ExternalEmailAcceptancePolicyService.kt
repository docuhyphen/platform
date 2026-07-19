package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeRecipientEligibilityException
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.organization.OrganizationExchangePolicyService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.Locale
import java.util.UUID

@ApplicationScoped
class ExternalEmailAcceptancePolicyService @Inject constructor(
    private val appUserService: AppUserService,
    private val organizationExchangePolicyService: OrganizationExchangePolicyService,
)
{
    fun validate(
        exchange: Exchange,
        recipientShare: Share,
        authenticatedAppUserId: UUID?,
    )
    {
        if (recipientShare.principalKind != PrincipalKind.USER)
        {
            deny()
        }
        val invitedUser = appUserService.getById(recipientShare.principalId) ?: deny()
        val normalizedEmail = invitedUser.email.trim().lowercase(Locale.ROOT)
        val currentAccount = appUserService.findRegisteredByEmail(normalizedEmail)

        if (authenticatedAppUserId != null)
        {
            if (currentAccount?.id != authenticatedAppUserId || invitedUser.id != authenticatedAppUserId)
            {
                deny()
            }
        }
        if (currentAccount != null && (!currentAccount.isActive || currentAccount.deprovisionedAt != null))
        {
            deny()
        }

        val initiatorId = exchange.initiator?.id ?: deny()
        try
        {
            organizationExchangePolicyService.assertCanShareWithUser(
                callerOrganizationId = exchange.ownerOrganizationId,
                initiatorAppUserId = initiatorId,
                recipientAppUserId = currentAccount?.id,
            )
        }
        catch (exception: IllegalArgumentException)
        {
            throw ExchangeRecipientEligibilityException(cause = exception)
        }
    }

    private fun deny(): Nothing = throw ExchangeRecipientEligibilityException()
}
