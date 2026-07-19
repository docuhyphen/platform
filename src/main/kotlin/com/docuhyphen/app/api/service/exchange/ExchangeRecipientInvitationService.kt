package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.ExchangeRecipientInvitationDtoTransformer
import com.docuhyphen.app.api.model.dto.ExchangeRecipientInvitationDto
import com.docuhyphen.app.api.model.entity.ExchangeAcceptanceDecision
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.util.UUID

@ApplicationScoped
class ExchangeRecipientInvitationService @Inject constructor(
    private val exchangeRecipientService: ExchangeRecipientService,
    private val authTokenContext: AuthTokenContext,
)
{
    fun listPending(): List<ExchangeRecipientInvitationDto>
    {
        val appUserId = authTokenContext.authToken.appUser?.id
            ?: throw ForbiddenException("A user account is required to view trusted participant invitations")
        return exchangeRecipientService.pendingTrustedParticipantInvitationsFor(appUserId)
            .map(ExchangeRecipientInvitationDtoTransformer::toDto)
    }

    @Transactional
    fun decide(
        recipientId: UUID,
        decision: ExchangeAcceptanceDecision,
    )
    {
        val appUserId = authTokenContext.authToken.appUser?.id
            ?: throw ForbiddenException("A user account is required to decide a trusted participant invitation")
        exchangeRecipientService.recordTrustedParticipantDecision(
            recipientId = recipientId,
            appUserId = appUserId,
            accepted = decision == ExchangeAcceptanceDecision.ACCEPT,
        )
    }
}
