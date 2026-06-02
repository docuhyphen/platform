package com.docuhyphen.app.api.service.sharingsession

import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.exception.SharingSessionNotFoundException
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.RoleName
import com.docuhyphen.app.api.repository.AppUserRepository
import com.docuhyphen.app.api.repository.ShareRepository
import com.docuhyphen.app.api.repository.SharingSessionRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Participant management over the unified [com.docuhyphen.app.api.model.entity.Share] model:
 * a participant is a `PARTICIPANT`-role USER share on the session. (The richer manage-access
 * API on SharingSessionResource covers arbitrary roles; this keeps the legacy participant
 * add/remove endpoints working.)
 */
@ApplicationScoped
class SharingSessionParticipantService @Inject constructor(
    private val sharingSessionRepository: SharingSessionRepository,
    private val appUserRepository: AppUserRepository,
    private val shareService: ShareService,
    private val shareRepository: ShareRepository,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionParticipantService::class.java)
    }

    @Transactional
    fun addSharingSessionParticipant(sessionId: String, participantId: String)
    {
        val sessionUuid = UUID.fromString(sessionId)
        sharingSessionRepository.findById(sessionUuid)
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        val participant = appUserRepository.findById(UUID.fromString(participantId))
            ?: throw AppUserNotFoundException("Participant not found")

        shareService.grant(
            resourceType = ResourceType.SHARING_SESSION,
            resourceId = sessionUuid,
            principalKind = PrincipalKind.USER,
            principalId = participant.id,
            roleName = RoleName.PARTICIPANT,
        )
        logger.info("Participant $participantId added to sharing session $sessionId")
    }

    @Transactional
    fun removeSharingSessionParticipant(sessionId: String, participantId: String)
    {
        val sessionUuid = UUID.fromString(sessionId)
        sharingSessionRepository.findById(sessionUuid)
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        val participant = appUserRepository.findById(UUID.fromString(participantId))
            ?: throw AppUserNotFoundException("Participant not found")

        shareRepository.findActiveForPrincipalOnResource(
            PrincipalKind.USER, participant.id, ResourceType.SHARING_SESSION, sessionUuid,
        ).forEach { shareService.revoke(it.id) }

        logger.info("Participant $participantId removed from sharing session $sessionId")
    }
}
