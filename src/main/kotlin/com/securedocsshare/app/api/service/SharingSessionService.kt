package com.securedocsshare.app.api.service

import com.securedocsshare.app.api.annotation.DocumentAuditRequired
import com.securedocsshare.app.api.exception.InvalidEmailException
import com.securedocsshare.app.api.exception.SessionNotFoundException
import com.securedocsshare.app.api.exception.UserNotFoundException
import com.securedocsshare.app.api.interceptor.AuthTokenContext
import com.securedocsshare.app.api.model.*
import com.securedocsshare.app.api.repository.AppUserRepository
import com.securedocsshare.app.api.repository.DocumentCommentRepository
import com.securedocsshare.app.api.repository.SharingSessionRepository
import com.securedocsshare.app.api.resource.model.SharingSessionParticipant
import com.securedocsshare.app.api.resource.model.SharingSessionRequestDocument
import com.securedocsshare.app.api.resource.model.UpdateSharingSessionRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class SharingSessionService @Inject constructor(
    private val sharingSessionRepository: SharingSessionRepository,
    private val appUserRepository: AppUserRepository,
    private val appUserService: AppUserService,
    private val emailService: EmailService,
    private val awsS3Service: AwsS3Service,
    private val documentAuditService: DocumentAuditService,
    private val authTokenContext: AuthTokenContext,
    private val documentCommentRepository: DocumentCommentRepository,
    private val authenticationService: AuthenticationService
)
{
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionService::class.java)
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
                this.type = doc.type
                this.restrictedType = doc.type
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

    @DocumentAuditRequired
    @Transactional
    fun uploadDocument(
        file: File,
        sessionId: String,
        documentId: String,
        performedBy: String
    )
    {
        var appUser = appUserService.findUserByEmail(performedBy)

        if (appUser == null)
        {
            logger.error("Failed to upload document, User not found using email: $performedBy")
        }

        appUser = appUserService.getAppUserById(UUID.fromString(performedBy))

        if (appUser == null)
        {
            logger.error("Failed to upload document, User not found using id: $performedBy")
            throw UserNotFoundException("User not found")
        }

        val sharingSession = sharingSessionRepository.findById(UUID.fromString(sessionId))
            ?: throw SessionNotFoundException("Sharing session not found")

        //ToDo: check if the uploader is in the session

        //ToDo: End To End encryption

        val document = sharingSession.documents.find { it.id == UUID.fromString(documentId) }
            ?: throw IllegalArgumentException("Session document not found")

//        val encryptionKey = awsS3Service.uploadDocument(file, bucketName, key)

        //ToDo: Encrypt the document
        //ToDo: Save the encryption key in the database
        //ToDo: Save the document in the database
        //ToDo: Save the document in the S3 bucket
        //ToDo: Log the action in the audit log
        //ToDo: Send an email to the receiver

        document.hash = "hash" //ToDo: Create a hash for the document

        sharingSessionRepository.update(sharingSession)
        documentAuditService.logAction(document, DocumentAuditLogAction.UPLOAD, appUser)
    }

    @Transactional
    fun addSharingSessionParticipant(
        sessionId: String,
        participantId: String,
        role: SharingSessionParticipantRole
    )
    {
        val sharingSession = sharingSessionRepository.findById(UUID.fromString(sessionId))
            ?: throw SessionNotFoundException("Sharing session not found")

        val participant = appUserRepository.findById(UUID.fromString(participantId))
            ?: throw UserNotFoundException("Participant not found")

        val sharingSessionParticipant = com.securedocsshare.app.api.model.SharingSessionParticipant().apply {
            this.appUser = participant
            this.role = role
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
            ?: throw SessionNotFoundException("Sharing session not found")

        val participant = appUserRepository.findById(UUID.fromString(participantId))
            ?: throw UserNotFoundException("Participant not found")

        sharingSession.participants.removeIf { it.appUser?.id == participant.id }

        sharingSessionRepository.update(sharingSession)

        logger.info("Participant removed from sharing session ${sharingSession.sessionName}")
    }

    fun getSharingSessionsForInitiator(initiatorId: UUID): List<SharingSession>
    {
        return sharingSessionRepository.findByInitiatorId(initiatorId)
    }

    fun getSharingSessionsForReceiver(receiverId: UUID): List<SharingSession>
    {
        return sharingSessionRepository.findByReceiverId(receiverId)
    }

    @DocumentAuditRequired
    @Transactional
    fun deleteDocument(
        sessionId: String,
        documentId: String
    )
    {
        val performedBy = authTokenContext.authToken.appUser!!.id.toString()

        val sharingSession = sharingSessionRepository.findById(UUID.fromString(sessionId))
            ?: throw SessionNotFoundException("Sharing session not found")

        val document = sharingSession.documents.find { it.id == UUID.fromString(documentId) }
            ?: throw SessionNotFoundException("Document not found")

        document.deleted = true
        document.updateDate = Timestamp.from(Instant.now())

        sharingSessionRepository.update(sharingSession)

        documentAuditService.logAction(document, DocumentAuditLogAction.DELETE, authTokenContext.authToken.appUser!!)
    }

    @Transactional
    fun addDocument(
        sessionId: String,
        documentType: DocumentType?,
        restrictedType: DocumentType?
    )
    {
        val sharingSession = sharingSessionRepository.findById(UUID.fromString(sessionId))
            ?: throw SessionNotFoundException("Sharing session not found")

        val document = Document().apply {
            this.createdDate = Timestamp.from(Instant.now())
            this.updateDate = Timestamp.from(Instant.now())
            this.deleted = false
            this.type = documentType

            this.restrictedType = restrictedType
        }

        sharingSession.documents.add(document)

        sharingSessionRepository.update(sharingSession)
    }

    @DocumentAuditRequired
    fun downloadDocument(
        sessionId: String,
        documentId: String
    ): File
    {
        val document = Document() // Retrieve the document entity as needed
//        val file = awsS3Service.downloadDocument(bucketName, key, encryptionKey)
//        documentAuditService.logAction(document, DocumentAuditLogAction.DOWNLOAD, performedBy)
        return File("file")
    }

    @DocumentAuditRequired
    @Transactional
    fun updateDocument(
        sessionId: String,
        documentId: String,
        title: String?,
        type: DocumentType?,
        restrictedType: DocumentType?
    )
    {
        val sharingSession = sharingSessionRepository.findById(UUID.fromString(sessionId))
            ?: throw SessionNotFoundException("Sharing session not found")

        val document = sharingSession.documents.find { it.id == UUID.fromString(documentId) }
            ?: throw SessionNotFoundException("Document not found")

        title ?: throw IllegalArgumentException("Title cannot be null")

        document.title = title
        document.type = type
        document.restrictedType = restrictedType

        sharingSessionRepository.update(sharingSession)

        documentAuditService.logAction(
            document,
            DocumentAuditLogAction.UPDATE,
            authTokenContext.authToken.appUser!!
        )
    }

    @Transactional
    fun addDocumentComment(
        documentId: String,
        commentText: String,
        commentedBy: String
    ): DocumentComment
    {
        val sharingSession = sharingSessionRepository.findById(UUID.fromString(documentId))
            ?: throw SessionNotFoundException("Document not found")

        val user = appUserRepository.findByEmail(commentedBy)
            ?: throw UserNotFoundException("User not found")

        val comment = DocumentComment().apply {
            this.commentText = commentText
            this.document = document //TODO: Add the document entity
            this.commentedBy = user
            this.createdDate = Timestamp.from(Instant.now())
        }

        documentCommentRepository.save(comment)
        return comment
    }

    fun getDocumentComments(documentId: String): List<DocumentComment>
    {
        return documentCommentRepository.findByDocumentId(UUID.fromString(documentId))
    }
}