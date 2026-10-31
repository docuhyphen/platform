package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.entity.ExchangeRecipientAttestation
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientAttestationService
import com.docuhyphen.app.api.service.exchange.TrustedGroupShareAttestationResolver
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Provider
import java.util.*

@ApplicationScoped
class InformationRequestTrustedGroupShareAttestationResolver @Inject constructor(
    private val partyRepository: InformationRequestPartyRepository,
    private val attestationServiceProvider: Provider<ExchangeRecipientAttestationService>,
) : TrustedGroupShareAttestationResolver {
    override fun attestationFor(share: Share): ExchangeRecipientAttestation? =
        share.takeIf { it.resourceType == ResourceType.INFORMATION_REQUEST }
            ?.let { partyRepository.findByShareId(it.id) }
            ?.exchangeRecipientId
            ?.let { attestationServiceProvider.get().findForRecipient(it) }

    override fun activeGroupShareIdsFor(exchangeRecipientId: UUID): List<UUID> =
        partyRepository.findActiveForExchangeRecipient(exchangeRecipientId)
            .filter { it.principalKind == PrincipalKind.PRINCIPAL_GROUP }
            .mapNotNull { it.shareId }
}
