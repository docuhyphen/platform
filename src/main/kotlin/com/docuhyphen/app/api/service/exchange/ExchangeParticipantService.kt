package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.util.*

@ApplicationScoped
class ExchangeParticipantService @Inject constructor(
    private val exchangeAccessManagementService: ExchangeAccessManagementService,
    private val shareRepository: ShareRepository,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeParticipantService::class.java)
    }

    @Transactional
    fun addExchangeParticipant(exchangeId: String, participantId: String)
    {
        val sessionUuid = UUID.fromString(exchangeId)
        exchangeAccessManagementService.grantAccess(
            exchangeId = sessionUuid,
            principalKind = PrincipalKind.USER.name,
            principalId = participantId,
            roleName = ExchangeShareRoleName.PARTICIPANT,
        )
        logger.info("Participant $participantId added to Exchange $exchangeId")
    }

    @Transactional
    fun removeExchangeParticipant(exchangeId: String, participantId: String)
    {
        val sessionUuid = UUID.fromString(exchangeId)
        val participantUuid = UUID.fromString(participantId)
        exchangeAccessManagementService.assertCanManageAccess(sessionUuid)

        shareRepository.findActiveForPrincipalOnResource(
            PrincipalKind.USER, participantUuid, ResourceType.EXCHANGE, sessionUuid,
        )
            .filter {
                it.roleName == ExchangeShareRoleName.PARTICIPANT.name &&
                        it.source == ShareSource.DIRECT
            }
            .forEach { exchangeAccessManagementService.revokeAccess(sessionUuid, it.id) }

        logger.info("Participant $participantId removed from Exchange $exchangeId")
    }
}
