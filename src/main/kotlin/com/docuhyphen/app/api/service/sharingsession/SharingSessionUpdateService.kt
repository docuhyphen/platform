package com.docuhyphen.app.api.service.sharingsession

import com.docuhyphen.app.api.exception.SharingSessionNotFoundException
import com.docuhyphen.app.api.model.entity.SharingSession
import com.docuhyphen.app.api.model.entity.SharingSessionStatus
import com.docuhyphen.app.api.repository.SharingSessionRepository
import com.docuhyphen.app.api.resource.model.UpdateSharingSessionRequest
import com.docuhyphen.app.api.service.communication.EmailService
import io.quarkus.security.ForbiddenException
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
class SharingSessionUpdateService @Inject constructor(
    private val sharingSessionRepository: SharingSessionRepository,
    private val emailService: EmailService,
)
{
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionUpdateService::class.java)
    }

    @Transactional
    fun updateSharingSession(
        sessionId: String,
        request: UpdateSharingSessionRequest?
    )
    {
        val sessionUUID = UUID.fromString(sessionId)

        sharingSessionRepository.findById(sessionUUID)
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        request?.sessionName?.let {
            sharingSessionRepository.updateSessionName(sessionUUID, it)
        }

        request?.description?.let {
            sharingSessionRepository.updateDescription(sessionUUID, it)
        }

        request?.status?.let {

            sharingSessionRepository.updateStatus(sessionUUID, it)

            if (it == SharingSessionStatus.ENDED)
            {
                sharingSessionRepository.updateEndDate(sessionUUID, Timestamp.from(Instant.now()))
            }
        }

        request?.rejectionReason?.let {
            sharingSessionRepository.updateRejectionReason(sessionUUID, it)
        }

        request?.requireRecipientSignIn?.let {
            sharingSessionRepository.updateRequireRecipientSignIn(sessionUUID, it)
        }

        request?.allowDocumentAddition?.let {
            sharingSessionRepository.updateAllowDocumentAddition(sessionUUID, it)
        }

        request?.allowDocumentDeletion?.let {
            sharingSessionRepository.updateAllowDocumentDeletion(sessionUUID, it)
        }

        request?.allowDocumentDownload?.let {
            sharingSessionRepository.updateAllowDocumentDownload(sessionUUID, it)
        }

        request?.allowDocumentUpdate?.let {
            sharingSessionRepository.updateAllowDocumentUpdate(sessionUUID, it)
        }

        request?.allowDocumentUpload?.let {
            sharingSessionRepository.updateAllowDocumentUpload(sessionUUID, it)
        }

        sharingSessionRepository.updateLastActivity(sessionUUID, Timestamp.from(Instant.now()))

        val updatedSession = sharingSessionRepository.findById(sessionUUID)!!

        val emailMessage = when (request?.status)
        {
            SharingSessionStatus.ACCEPTED_STARTED -> "Sharing Session ${updatedSession.sessionName} has been accepted and started"
            SharingSessionStatus.ENDED -> "Session ${updatedSession.sessionName} has completed and further modifications will not be possible."
            SharingSessionStatus.REJECTED -> "Your request has been rejected by the session recipient. Reason: ${request.rejectionReason}"
            else -> null
        }

        if (request?.status == SharingSessionStatus.ENDED)
        {
            emailMessage?.let {
                emailService.sendEmail(
                    updatedSession.recipient?.email!!, "Sharing Session Status Update | ${request.status}", emailMessage
                )
            }
        }

        if (request?.status == SharingSessionStatus.ENDED ||
            request?.status == SharingSessionStatus.REJECTED ||
            request?.status == SharingSessionStatus.ACCEPTED_STARTED
        )
        {
            emailMessage?.let {
                emailService.sendEmail(
                    updatedSession.initiator?.email!!, "Sharing Session Status Update | ${request.status}", emailMessage
                )
            }
        }
        emailMessage?.let {
            emailService.sendEmail(
                updatedSession.initiator?.email!!,
                "Sharing Session Status Update | ${request?.status}",
                emailMessage
            )
        }

        logger.info("Sharing session ${updatedSession.sessionName} completed")
    }

    fun deleteSharingSession(sessionId: String?)
    {
        if (sessionId == null)
        {
            throw IllegalArgumentException("Session ID cannot be null")
        }

        val sessionUUID = UUID.fromString(sessionId)

        var session = sharingSessionRepository.findById(sessionUUID)?.apply {

            isDeleted = true
            dateDeleted = Timestamp.from(Instant.now())

        } ?: throw SharingSessionNotFoundException("Sharing session not found")

        sharingSessionRepository.update(session)

        logger.info("Sharing session ${session.sessionName} deleted")
    }

    fun updateNoAuthSharingSession(
        sessionId: String,
        sessionStatus: SharingSessionStatus?,
        otp: String?,
        rejectReason: String?
    ): SharingSession
    {
        val sessionUUID = UUID.fromString(sessionId)

        val session = sharingSessionRepository.findById(sessionUUID)
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        if (session.requireRecipientSignIn)
        {
            logger.error("Attempted to access a sharing session that requires recipient sign-in")
            throw ForbiddenException("Sharing session not found")
        }

        if (session.status != SharingSessionStatus.ACCEPTED_STARTED && session.status != SharingSessionStatus.INITIATED)
        {
            logger.error("Attempted to update a sharing session with an invalid status")
            throw IllegalArgumentException("Sharing session not found")
        }

        if (session.status == SharingSessionStatus.ENDED || session.status == SharingSessionStatus.REJECTED)
        {
            logger.error("Attempted to update a sharing session that has ended or rejected: $sessionStatus")
            throw IllegalArgumentException("Sharing session not found")
        }

        //ToDo: validate otp

        sessionStatus?.let {
            sharingSessionRepository.updateStatus(sessionUUID, it)

            if (it == SharingSessionStatus.REJECTED)
            {
                rejectReason?.let {
                    sharingSessionRepository.updateRejectionReason(sessionUUID, it)
                }

                sharingSessionRepository.updateEndDate(sessionUUID, Timestamp.from(Instant.now()))
            }
        }

        sharingSessionRepository.updateLastActivity(sessionUUID, Timestamp.from(Instant.now()))

        logger.info("Sharing session ${session.sessionName} updated")

        return sharingSessionRepository.findById(sessionUUID)!!
    }
}