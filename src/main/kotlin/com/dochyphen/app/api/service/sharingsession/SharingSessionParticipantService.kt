package com.dochyphen.app.api.service.sharingsession

import com.dochyphen.app.api.exception.SharingSessionNotFoundException
import com.dochyphen.app.api.exception.UserNotFoundException
import com.dochyphen.app.api.model.entity.SharingSessionParticipant
import com.dochyphen.app.api.repository.AppUserRepository
import com.dochyphen.app.api.repository.SharingSessionRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class SharingSessionParticipantService @Inject constructor(
    private val sharingSessionRepository: SharingSessionRepository,
    private val appUserRepository: AppUserRepository
)
{
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionParticipantService::class.java)
    }

    @Transactional
    fun addSharingSessionParticipant(
        sessionId: String,
        participantId: String
    )
    {
        val sharingSession = sharingSessionRepository.findById(UUID.fromString(sessionId))
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        val participant = appUserRepository.findById(UUID.fromString(participantId))
            ?: throw UserNotFoundException("Participant not found")

        val sharingSessionParticipant = SharingSessionParticipant().apply {
            this.appUser = participant
            this.addedDate = Timestamp.from(Instant.now())
        }

        sharingSession.participants.add(sharingSessionParticipant)

        sharingSessionRepository.update(sharingSession)

        logger.info("Participant added to sharing session ${sharingSession.sessionName}")
    }

    fun removeSharingSessionParticipant(
        sessionId: String,
        participantId: String
    )
    {
        val sharingSession = sharingSessionRepository.findById(UUID.fromString(sessionId))
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        val participant = appUserRepository.findById(UUID.fromString(participantId))
            ?: throw UserNotFoundException("Participant not found")

        sharingSession.participants.removeIf { it.appUser?.id == participant.id }

        sharingSessionRepository.update(sharingSession)

        logger.info("Participant removed from sharing session ${sharingSession.sessionName}")
    }
}