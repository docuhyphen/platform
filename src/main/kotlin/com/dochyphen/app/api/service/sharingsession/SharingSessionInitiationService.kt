package com.dochyphen.app.api.service.sharingsession

import com.dochyphen.app.api.exception.InvalidEmailException
import com.dochyphen.app.api.exception.UserNotFoundException
import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.model.entity.*
import com.dochyphen.app.api.repository.AppUserRepository
import com.dochyphen.app.api.repository.SharingSessionRepository
import com.dochyphen.app.api.resource.model.SharingSessionParticipantRequest
import com.dochyphen.app.api.resource.model.SharingSessionRequestDocumentRequest
import com.dochyphen.app.api.service.AppUserService
import com.dochyphen.app.api.service.auth.AuthenticationService
import com.dochyphen.app.api.service.communication.AppNotificationService
import com.dochyphen.app.api.service.communication.EmailService
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
class SharingSessionInitiationService @Inject constructor(
    private val sharingSessionRepository: SharingSessionRepository,
    private val appUserRepository: AppUserRepository,
    private val appUserService: AppUserService,
    private val emailService: EmailService,
    private val authTokenContext: AuthTokenContext,
    private val authenticationService: AuthenticationService,
    private val appNotificationService: AppNotificationService
)
{
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionInitiationService::class.java)
    }
    @Transactional
    fun initiateSharingSession(
        initialShareMessage: String?,
        description: String?,
        recipientEmail: String?,
        sessionName: String?,
        sessionDocuments: List<SharingSessionRequestDocumentRequest>?,
        requestRecipientSignIn: Boolean? = false,
        allowDocumentAddition: Boolean? = false,
        allowDocumentDeletion: Boolean? = false,
        allowDocumentDownload: Boolean? = false,
        allowDocumentUpdate: Boolean? = false,
        allowDocumentUpload: Boolean? = false,
        sharingSessionParticipantRequests: List<SharingSessionParticipantRequest>? = mutableListOf()
    ): SharingSession
    {
        val initiator = authTokenContext.authToken.appUser

        if (initiator?.email == recipientEmail)
        {
            throw IllegalArgumentException("Recipient and Initiator cannot be the same")
        }

        if (sessionDocuments.isNullOrEmpty())
        {
            throw IllegalArgumentException("Session documents cannot be empty")
        }

        val recipient = recipientEmail?.let {
            if (authenticationService.isEmailInvalid(it))
            {
                throw InvalidEmailException("Recipient email is invalid")
            }
            appUserRepository.findByEmail(it) ?: AppUser().apply {
                isTemporary = true
                email = it
                isActive = false
            }
        }

        if (recipient == null)
        {
            throw IllegalArgumentException("Recipient cannot be null")
        }

//        if (recipient.isTemporary)
//        {
//            throw IllegalArgumentException("This email still needs to create an account")
//        }

        val participants = sharingSessionParticipantRequests?.map {

            val appUser = appUserService.getAppUserById(UUID.fromString(it.id))
                ?: throw UserNotFoundException("One of the participants not found")

            SharingSessionParticipant().apply {
                this.appUser = appUser
                this.role = it.role
                this.addedDate = Timestamp.from(Instant.now())
            }
        }?.toMutableList() ?: mutableListOf()

        entityManager.detach(initiator)
        entityManager.detach(recipient)

        val sharingSession = SharingSession().apply {
            this.initiator = entityManager.merge(initiator)
            this.recipient = entityManager.merge(recipient)
            this.sessionName = sessionName
            this.initialShareMessage = initialShareMessage
            this.description = description
            this.status = SharingSessionStatus.INITIATED
            this.createdDate = Timestamp.from(Instant.now())
            this.lastActivity = Timestamp.from(Instant.now())
            this.requestRecipientSignIn = requestRecipientSignIn == true
            this.allowDocumentAddition = allowDocumentAddition == true
            this.allowDocumentDeletion = allowDocumentDeletion == true
            this.allowDocumentDownload = allowDocumentDownload == true
            this.allowDocumentUpdate = allowDocumentUpdate == true
            this.allowDocumentUpload = allowDocumentUpload == true
            this.participants = participants
        }

        sessionDocuments.forEach { doc ->
            val document = Document().apply {
                this.title = doc.title
                this.createdDate = Timestamp.from(Instant.now())
                this.updateDate = Timestamp.from(Instant.now())
                this.uploadDate = null
                this.isDeleted = false
                this.type = null // Type is set on the document upload method
                this.restrictedType = doc.restrictedType
            }
            sharingSession.documents.add(document)
        }

        val savedSharingSession = sharingSessionRepository.save(sharingSession)

        val initiatorCompany = initiator?.person?.contactDetails?.company?.name ?: "N/A"

        emailService.sendEmail(
            recipientEmail,
            "Document Request from ${initiator?.person?.firstName} ${initiator?.person?.lastName}",
            """
            You have been requested to upload the following documents: ${sessionDocuments.joinToString(", ")}.
            Please use the following link to upload your documents: [link]

            Company Details: $initiatorCompany

            If you do not recognize this request, please report it here: [report_link]
        """.trimIndent()
        )

        emailService.sendEmail(
            initiator!!.email,
            "Document Request Sent to ${recipient.email}",
            "You have successfully requested ${recipient.email} to upload the following documents: ${
                sessionDocuments.joinToString(
                    ", "
                )
            }."
        )

        logger.info("Sharing session initiated by ${initiator?.email} for ${recipient.email}")

        appNotificationService.sendNotification(
            recipient.id.toString(),
            "Document Request from ${initiator.person?.firstName} ${initiator.person?.lastName}",
            "You have been requested to upload the following documents: ${
                sessionDocuments.joinToString(
                    ", "
                )
            }."
        )

        return savedSharingSession
    }
}