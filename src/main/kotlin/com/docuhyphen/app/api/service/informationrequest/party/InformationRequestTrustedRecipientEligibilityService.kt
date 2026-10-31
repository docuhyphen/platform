package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.exception.OrganizationTrustException
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestTrustedRecipientEligibilityService @Inject constructor(
    private val exchangeRecipientService: ExchangeRecipientService,
    private val partyRepository: InformationRequestPartyRepository,
) {
    fun requireAssignable(recipientId: UUID, exchangeId: UUID, principal: PrincipalRef) {
        if (exchangeRecipientService.trustSuspended(recipientId)) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.TRUST_SUSPENDED,
                "The trust relationship with this recipient's organization is suspended",
            )
        }
        try {
            exchangeRecipientService.requireAssignablePartyRecipient(recipientId, exchangeId, principal)
        } catch (_: OrganizationTrustException) {
            throw recipientUnavailable()
        } catch (_: IllegalArgumentException) {
            throw recipientUnavailable()
        }
    }

    fun requireIssuable(request: InformationRequest) {
        partyRepository.findActiveForRequest(request.id).forEach { party ->
            val recipientId = party.exchangeRecipientId ?: return@forEach
            val kind = party.principalKind ?: return@forEach
            val principalId = party.principalId ?: return@forEach
            requireAssignable(recipientId, request.exchangeId, PrincipalRef(kind, principalId))
        }
    }

    private fun recipientUnavailable() =
        InformationRequestLifecycleException(
            InformationRequestErrorCatalog.TRUSTED_RECIPIENT_UNAVAILABLE,
            "This Exchange recipient can no longer be assigned to the request",
        )
}
