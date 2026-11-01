package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.dto.SessionAccessEntryDto
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Provider
import java.util.*

@ApplicationScoped
class ShareQueryService @Inject constructor(
    private val shareRepository: ShareRepository,
    private val principalDisplayService: PrincipalDisplayService,
    private val exchangeRecipientServiceProvider: Provider<ExchangeRecipientService>,
)
{
    fun getSessionAccessView(exchangeId: UUID): List<SessionAccessEntryDto>
    {
        val recipientPurposes = exchangeRecipientServiceProvider.get().findByExchangeId(exchangeId)
            .associate { recipient -> recipient.directShareId to recipient.purpose.name }
        return shareRepository.findAllByResource(ResourceType.EXCHANGE, exchangeId)
            .filter { it.source != ShareSource.INHERITED_FROM_GROUP }
            .map { share -> share.toAccessEntry(recipientPurposes[share.id]) }
    }

    private fun Share.toAccessEntry(recipientPurpose: String?): SessionAccessEntryDto =
        SessionAccessEntryDto(
            shareId = id,
            principalKind = principalKind.name,
            principalId = principalId,
            displayName = principalDisplayService.display(PrincipalRef(principalKind, principalId)).name,
            roleName = exchangeRoleName(),
            source = source.name,
            status = status.name,
            recipientPurpose = recipientPurpose,
            grantedByPrincipalKind = grantedByPrincipalKind?.name,
            grantedByPrincipalId = grantedByPrincipalId,
            grantedAt = grantedAt,
            expiresAt = expiresAt,
            constraintsJson = constraintsJson,
        )
}
