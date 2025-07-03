package com.dochyphen.app.api.service.sharingsession

import com.dochyphen.app.api.exception.InvalidEmailException
import com.dochyphen.app.api.exception.AppUserNotFoundException
import com.dochyphen.app.api.exception.OrganizationGroupNotFoundException
import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.model.entity.*
import com.dochyphen.app.api.model.entity.SharingSessionRecipientType.APP_USER
import com.dochyphen.app.api.model.entity.SharingSessionRecipientType.EMAIL
import com.dochyphen.app.api.model.entity.SharingSessionRecipientType.GROUP
import com.dochyphen.app.api.repository.AppUserRepository
import com.dochyphen.app.api.repository.SharingSessionRepository
import com.dochyphen.app.api.resource.model.SharingSessionInitiationDto
import com.dochyphen.app.api.service.AppUserService
import com.dochyphen.app.api.service.auth.AuthenticationService
import com.dochyphen.app.api.service.communication.AppNotificationService
import com.dochyphen.app.api.service.communication.EmailService
import com.dochyphen.app.api.service.organization.OrganizationGroupService
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
    private val appNotificationService: AppNotificationService,
    private val orgGroupService: OrganizationGroupService
)
{
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionInitiationService::class.java)
    }

    @Transactional
    fun initiateSharingSession(sessionInitiationDto: SharingSessionInitiationDto): SharingSession
    {
        val initiator = authTokenContext.authToken.appUser!!

        validateSharingSessionFields(initiator, sessionInitiationDto)

        val recipient = when (sessionInitiationDto.recipientType)
        {
            EMAIL ->
                appUserService.getAppUserByEmail(sessionInitiationDto.recipientEmail!!)
                    ?: AppUser().apply {
                        isTemporary = true;
                        email = sessionInitiationDto.recipientEmail!!;
                        isActive = false
                    }

            APP_USER ->
                appUserService.getById(UUID.fromString(sessionInitiationDto.recipientAppUserId))!!

            GROUP ->
                orgGroupService.getById(sessionInitiationDto.recipientOrgGroupId!!)

            else ->
                throw IllegalArgumentException("Unsupported recipient type")
        }

        val participants = sessionInitiationDto.participants?.map {

            SharingSessionParticipant().apply {
                this.appUser = appUser
                this.addedDate = Timestamp.from(Instant.now())
            }
        }?.toMutableList() ?: mutableListOf()

        entityManager.detach(initiator)
        entityManager.detach(recipient)

        val appUserRecipient =
            if (sessionInitiationDto.recipientType != GROUP) entityManager.merge(recipient as AppUser) else null
        val orgGroupRecipient =
            if (sessionInitiationDto.recipientType == GROUP) entityManager.merge(recipient as OrganizationGroup) else null

        val sharingSession = SharingSession().apply {
            this.initiator = entityManager.merge(initiator)
            this.recipient = appUserRecipient
            this.recipientGroup = orgGroupRecipient
            this.sessionName = sessionInitiationDto.sessionName!!.trim()
            this.initialShareMessage = sessionInitiationDto.initialShareMessage?.trim()
            this.description = sessionInitiationDto.description?.trim()
            this.status = SharingSessionStatus.INITIATED
            this.createdDate = Timestamp.from(Instant.now())
            this.lastActivity = Timestamp.from(Instant.now())
            this.requireRecipientSignIn = sessionInitiationDto.requestRecipientSignIn == true
            this.allowDocumentAddition = sessionInitiationDto.allowDocumentAddition == true
            this.allowDocumentDeletion = sessionInitiationDto.allowDocumentDeletion == true
            this.allowDocumentDownload = sessionInitiationDto.allowDocumentDownload == true
            this.allowDocumentUpdate = sessionInitiationDto.allowDocumentUpdate == true
            this.allowDocumentUpload = sessionInitiationDto.allowDocumentUpload == true
            this.participants = participants
        }

        sessionInitiationDto.sessionDocuments?.forEach { doc ->
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

        logger.info("Sharing session initiated ID: ${sharingSession.id}")
        return savedSharingSession
    }

    fun validateSharingSessionFields(
        initiator: AppUser,
        sessionInitiationDto: SharingSessionInitiationDto
    )
    {
        if(sessionInitiationDto.sessionName.isNullOrBlank())
        {
            throw IllegalArgumentException("Session name is required")
        }

        when (sessionInitiationDto.recipientType)
        {
            EMAIL ->
            {
                if (initiator.email == sessionInitiationDto.recipientEmail)
                {
                    throw IllegalArgumentException("Recipient and Initiator cannot be the same")
                }

                sessionInitiationDto.recipientEmail?.let {
                    if (authenticationService.isEmailInvalid(it))
                    {
                        throw InvalidEmailException("Recipient email is invalid")
                    }
                } ?: throw InvalidEmailException("Recipient email is required")
            }

            APP_USER ->
            {
                sessionInitiationDto.recipientAppUserId?.let {

                    appUserService.getById(UUID.fromString(it))
                        ?: throw AppUserNotFoundException("Recipient not found")

                } ?: throw AppUserNotFoundException("Recipient not found")
            }

            GROUP ->
            {
                sessionInitiationDto.recipientOrgGroupId?.let {

                    orgGroupService.getById(it)
                        ?: throw OrganizationGroupNotFoundException("Recipient group not found")

                } ?: throw OrganizationGroupNotFoundException("Recipient group not found")
            }

            else ->
                throw IllegalArgumentException("Unsupported recipient type")
        }

        if (sessionInitiationDto.sessionDocuments.isNullOrEmpty())
        {
            throw IllegalArgumentException("Session documents cannot be empty")
        }

        sessionInitiationDto.participants?.map {

            appUserService.getById(UUID.fromString(it.id))
                ?: throw AppUserNotFoundException("One of the participants not found")
        }
    }

    fun sendNotifications(
        recipientType: SharingSessionRecipientType,
        initiator: AppUser,
        recipientAppUser: AppUser?,
        recipientOrgGroup: OrganizationGroup?,
        documents: List<Document>
    )
    {
//        val initiatorOrganization = initiator?.person?.contactDetails?.organization?.name ?: "N/A"
//
//        if (recipientType != GROUP)
//        {
//            val recipientEmail =
//                emailService.sendEmail(
//                    recipientEmail,
//                    "Document Request from ${initiator?.person?.firstName} ${initiator?.person?.lastName}",
//                    """
//                You have been requested to upload the following documents: ${sessionDocuments.joinToString(", ")}.
//                Please use the following link to upload your documents: [link]
//
//                Organization Details: $initiatorOrganization
//
//                If you do not recognize this request, please report it here: [report_link]
//            """.trimIndent()
//                )
//
//        }
//        else if (recipientType == GROUP)
//        {
//
//        }
//
//        emailService.sendEmail(
//            initiator!!.email,
//            "Document Request Sent to ${recipient.email}",
//            "You have successfully requested ${recipient.email} to upload the following documents: ${
//                sessionDocuments.joinToString(
//                    ", "
//                )
//            }."
//        )
//
//        appNotificationService.sendNotification(
//            recipient.id.toString(),
//            "Document Request from ${initiator.person?.firstName} ${initiator.person?.lastName}",
//            "You have been requested to upload the following documents: ${
//                sessionDocuments.joinToString(
//                    ", "
//                )
//            }."
//        )
    }
}