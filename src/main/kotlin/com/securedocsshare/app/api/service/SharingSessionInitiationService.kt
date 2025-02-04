package com.securedocsshare.app.api.service

import com.securedocsshare.app.api.exception.InvalidEmailException
import com.securedocsshare.app.api.exception.UserNotFoundException
import com.securedocsshare.app.api.interceptor.AuthTokenContext
import com.securedocsshare.app.api.model.AppUser
import com.securedocsshare.app.api.model.Document
import com.securedocsshare.app.api.model.SharingSession
import com.securedocsshare.app.api.model.SharingSessionStatus
import com.securedocsshare.app.api.repository.AppUserRepository
import com.securedocsshare.app.api.repository.DocumentCommentRepository
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
    ): SharingSession
    {//ToDo: check if receiver and initiator are the same
        val initiator = authTokenContext.authToken.appUser
        var receiver = receiverEmail?.let { appUserRepository.findByEmail(receiverEmail) }

        var tempAppUser: AppUser? = receiverEmail?.let {

            if (authenticationService.isEmailInvalid(it))
            {
                throw InvalidEmailException("Receiver email is invalid")
            }

            AppUser().apply {
                isTemporary = true
                email = receiverEmail
                isActive = false
            }
        }

        if (receiver == null)
        {
            receiver = tempAppUser
        }
        else
        {
            //ToDo: this logic can be better
            if (receiver.isTemporary)
            {
//                throw IllegalArgumentException("This email still needs to create an account")
            }
        }

        //ToDo: check if the receiver is null

        if (sessionDocuments.isNullOrEmpty())
        {
            throw IllegalArgumentException("Session documents cannot be empty")
        }

        val participants = mutableListOf<com.securedocsshare.app.api.model.SharingSessionParticipant>()

        sharingSessionParticipants?.forEach {
            val appUser = appUserService.getAppUserById(UUID.fromString(it.id)) ?: run {
                logger.error("Participant not found with id: ${it.id}")
                throw UserNotFoundException("One of the participants participants not found")
            }

            val sharingSessionParticipant = com.securedocsshare.app.api.model.SharingSessionParticipant().apply {
                this.appUser = appUser
                this.role = it.role
                this.addedDate = Timestamp.from(Instant.now())
            }

            participants.add(sharingSessionParticipant)
        }

        if (initiator != null)
        {
            entityManager.detach(initiator)
        }

        if (receiver != null)
        {
            entityManager.detach(receiver)
        }

        val sharingSession = SharingSession().apply {
            this.initiator = entityManager.merge(initiator)
            this.receiver = if (receiver == null) null else entityManager.merge(receiver)
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
                this.type = null //Type is set on the document upload method
                this.restrictedType = doc.restrictedType
            }

            sharingSession.documents.add(document)
        }

        var savedSharingSession = sharingSessionRepository.save(sharingSession)

        val receiverDetails = receiver
        val initiatorCompany = initiator?.person?.contactDetails?.company?.name ?: "N/A"

        emailService.sendEmail(
            receiverEmail!!,
            "Document Request from ${initiator?.person?.firstName} ${initiator?.person?.lastName}",
            """
                You have been requested to upload the following documents: ${sessionDocuments.joinToString(", ")}.
                Please use the following link to upload your documents: [link]

                Company Details: $initiatorCompany

           If you do not recognize this request, please report it here: [report_link]""".trimMargin()
        )

        emailService.sendEmail(
            initiator?.email!!,
            "Document Request Sent to ${receiverDetails?.email}",
            "You have successfully requested ${receiverDetails?.email} to upload the following documents: ${
                sessionDocuments.joinToString(
                    ", "
                )
            }."
        )

        logger.info("Sharing session initiated by ${initiator.email} for ${receiverDetails?.email}")

        return savedSharingSession
    }
}