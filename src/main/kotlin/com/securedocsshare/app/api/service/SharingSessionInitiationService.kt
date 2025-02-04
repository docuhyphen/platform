package com.securedocsshare.app.api.service

import com.securedocsshare.app.api.exception.InvalidEmailException
import com.securedocsshare.app.api.exception.UserNotFoundException
import com.securedocsshare.app.api.interceptor.AuthTokenContext
import com.securedocsshare.app.api.model.AppUser
import com.securedocsshare.app.api.model.Document
import com.securedocsshare.app.api.model.SharingSession
import com.securedocsshare.app.api.model.SharingSessionModelConverter
import com.securedocsshare.app.api.model.SharingSessionStatus
import com.securedocsshare.app.api.model.dto.SharingSessionBasicDto
import com.securedocsshare.app.api.repository.AppUserRepository
import com.securedocsshare.app.api.repository.SharingSessionRepository
import com.securedocsshare.app.api.resource.model.SharingSessionParticipant
import com.securedocsshare.app.api.resource.model.SharingSessionRequestDocument
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
    private val authenticationService: AuthenticationService
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
        receiverEmail: String?,
        sessionName: String?,
        sessionDocuments: List<SharingSessionRequestDocument>?,
        requestReceiverSignIn: Boolean? = false,
        allowDocumentAddition: Boolean? = false,
        allowDocumentDeletion: Boolean? = false,
        allowDocumentDownload: Boolean? = false,
        allowDocumentUpdate: Boolean? = false,
        allowDocumentUpload: Boolean? = false,
        sharingSessionParticipants: List<SharingSessionParticipant>? = mutableListOf()
    ): SharingSessionBasicDto
    {
        val initiator = authTokenContext.authToken.appUser

        if (initiator?.email == receiverEmail)
        {
            throw IllegalArgumentException("Receiver and initiator cannot be the same")
        }

        if (sessionDocuments.isNullOrEmpty())
        {
            throw IllegalArgumentException("Session documents cannot be empty")
        }

        val receiver = receiverEmail?.let {
            if (authenticationService.isEmailInvalid(it))
            {
                throw InvalidEmailException("Receiver email is invalid")
            }
            appUserRepository.findByEmail(it) ?: AppUser().apply {
                isTemporary = true
                email = it
                isActive = false
            }
        }

        if (receiver == null)
        {
            throw IllegalArgumentException("Receiver cannot be null")
        }

//        if (receiver.isTemporary)
//        {
//            throw IllegalArgumentException("This email still needs to create an account")
//        }

        val participants = sharingSessionParticipants?.map {
            val appUser = appUserService.getAppUserById(UUID.fromString(it.id))
                ?: throw UserNotFoundException("One of the participants not found")
            com.securedocsshare.app.api.model.SharingSessionParticipant().apply {
                this.appUser = appUser
                this.role = it.role
                this.addedDate = Timestamp.from(Instant.now())
            }
        }?.toMutableList() ?: mutableListOf()

        entityManager.detach(initiator)
        entityManager.detach(receiver)

        val sharingSession = SharingSession().apply {
            this.initiator = entityManager.merge(initiator)
            this.receiver = entityManager.merge(receiver)
            this.sessionName = sessionName
            this.initialShareMessage = initialShareMessage
            this.description = description
            this.status = SharingSessionStatus.INITIATED
            this.createdDate = Timestamp.from(Instant.now())
            this.lastActivity = Timestamp.from(Instant.now())
            this.requestReceiverSignIn = requestReceiverSignIn == true
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
                this.deleted = false
                this.type = null // Type is set on the document upload method
                this.restrictedType = doc.restrictedType
            }
            sharingSession.documents.add(document)
        }

        val savedSharingSession = sharingSessionRepository.save(sharingSession)

        val initiatorCompany = initiator?.person?.contactDetails?.company?.name ?: "N/A"

        emailService.sendEmail(
            receiverEmail,
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
            "Document Request Sent to ${receiver.email}",
            "You have successfully requested ${receiver.email} to upload the following documents: ${
                sessionDocuments.joinToString(
                    ", "
                )
            }."
        )

        logger.info("Sharing session initiated by ${initiator?.email} for ${receiver.email}")

        return SharingSessionModelConverter.Companion.convertToBasicDto(savedSharingSession)
    }
}