package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientService
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.user.AppUserService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestRecipientAddressResolver @Inject constructor(
    private val shareService: ShareService,
    private val exchangeRecipientService: ExchangeRecipientService,
    private val appUserService: AppUserService,
) {
    fun recipientAccountOf(exchangeId: UUID, email: String): PrincipalRef? {
        val address = normalized(email)
        return shareService.activeUserPrincipalIds(ResourceType.EXCHANGE, exchangeId)
            .firstOrNull { userId -> addressOf(userId) == address }
            ?.let(PrincipalRef::user)
    }

    fun isTrustedPersonAddress(exchangeId: UUID, email: String): Boolean {
        val address = normalized(email)
        return exchangeRecipientService.findByExchangeId(exchangeId)
            .filter {
                it.selectionType == ExchangeRecipientSelectionType.TRUSTED_PERSON &&
                        it.acceptanceStatus == ExchangeRecipientAcceptanceStatus.ACCEPTED
            }
            .mapNotNull { recipient ->
                shareService.getById(recipient.directShareId)
                    ?.takeIf { it.status == ShareStatus.ACTIVE && it.principalKind == PrincipalKind.USER }
            }
            .any { share -> addressOf(share.principalId) == address }
    }

    private fun addressOf(userId: UUID): String? = appUserService.getById(userId)?.email?.let(::normalized)

    private fun normalized(email: String): String = email.trim().lowercase()
}
