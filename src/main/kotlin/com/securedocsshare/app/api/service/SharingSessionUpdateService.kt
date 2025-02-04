package com.securedocsshare.app.api.service

import com.securedocsshare.app.api.exception.SessionNotFoundException
import com.securedocsshare.app.api.model.SharingSessionStatus
import com.securedocsshare.app.api.repository.SharingSessionRepository
import com.securedocsshare.app.api.resource.model.UpdateSharingSessionRequest
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

        sharingSessionRepository.findById(sessionUUID) ?: throw SessionNotFoundException("Sharing session not found")

        request?.sessionName?.let {
            sharingSessionRepository.updateSessionName(sessionUUID, it)
        }

        request?.status?.let {
            sharingSessionRepository.updateStatus(sessionUUID, it)
        }

        request?.rejectionReason?.let {
            sharingSessionRepository.updateRejectionReason(sessionUUID, it)
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
            SharingSessionStatus.COMPLETED -> "Session ${updatedSession.sessionName} has completed and further modifications will not be possible."
            SharingSessionStatus.REJECTED -> "Your request has been rejected by the receiver. Reason: ${request.rejectionReason}"
            else -> null
        }

        if (request?.status == SharingSessionStatus.COMPLETED)
        {
            emailMessage?.let {
                emailService.sendEmail(
                    updatedSession.receiver?.email!!, "Sharing Session Status Update | ${request.status}", emailMessage
                )
            }
        }

        if (request?.status == SharingSessionStatus.COMPLETED ||
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
}