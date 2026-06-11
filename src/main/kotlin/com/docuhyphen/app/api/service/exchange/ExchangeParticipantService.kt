package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.RoleName
import com.docuhyphen.app.api.repository.AppUserRepository
import com.docuhyphen.app.api.repository.ShareRepository
import com.docuhyphen.app.api.repository.ExchangeRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Participant management over the unified [com.docuhyphen.app.api.model.entity.Share] model:
 * a participant is a `PARTICIPANT`-role USER share on the session. (The richer manage-access
 * API on ExchangeResource covers arbitrary roles; this keeps the legacy participant
 * add/remove endpoints working.)
 */
@ApplicationScoped
class ExchangeParticipantService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
    private val appUserRepository: AppUserRepository,
    private val shareService: ShareService,
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
        exchangeRepository.findById(sessionUuid)
            ?: throw ExchangeNotFoundException("Exchange not found")

        val participant = appUserRepository.findById(UUID.fromString(participantId))
            ?: throw AppUserNotFoundException("Participant not found")

        shareService.grant(
            resourceType = ResourceType.EXCHANGE,
            resourceId = sessionUuid,
            principalKind = PrincipalKind.USER,
            principalId = participant.id,
            roleName = RoleName.PARTICIPANT,
        )
        logger.info("Participant $participantId added to exchange $exchangeId")
    }

    @Transactional
    fun removeExchangeParticipant(exchangeId: String, participantId: String)
    {
        val sessionUuid = UUID.fromString(exchangeId)
        exchangeRepository.findById(sessionUuid)
            ?: throw ExchangeNotFoundException("Exchange not found")

        val participant = appUserRepository.findById(UUID.fromString(participantId))
            ?: throw AppUserNotFoundException("Participant not found")

        shareRepository.findActiveForPrincipalOnResource(
            PrincipalKind.USER, participant.id, ResourceType.EXCHANGE, sessionUuid,
        ).forEach { shareService.revoke(it.id) }

        logger.info("Participant $participantId removed from exchange $exchangeId")
    }
}
