package com.docuhyphen.app.api.service.sharingsession

import com.docuhyphen.app.api.exception.InvalidEmailException
import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.exception.OrganizationGroupNotFoundException
import com.docuhyphen.app.api.extension.normalizeEmailOrNull
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.entity.SharingSessionRecipientType.APP_USER
import com.docuhyphen.app.api.model.entity.SharingSessionRecipientType.EMAIL
import com.docuhyphen.app.api.model.entity.SharingSessionRecipientType.GROUP
import com.docuhyphen.app.api.repository.AppUserRepository
import com.docuhyphen.app.api.repository.SharingSessionRepository
import com.docuhyphen.app.api.resource.model.SharingSessionInitiationDto
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthRateLimitService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.communication.AppNotificationService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
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
    private val emailTemplateService: EmailTemplateService,
    private val authTokenContext: AuthTokenContext,
    private val authenticationService: AuthenticationService,
    private val authRateLimitService: AuthRateLimitService,
    private val configurationService: ConfigurationService,
    private val authAuditService: AuthAuditService,
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

        if (sessionInitiationDto.recipientType == EMAIL)
        {
            val limited = authRateLimitService.isLimited(
                key = "directory:recipient-resolve:${initiator.id}",
                maxPerMinute = configurationService.getAuthRateLimitLookupPerMinute(),
            )
            if (limited)
            {
                authAuditService.emit(
                    action = "RECIPIENT_RESOLVE",
                    outcome = "DENY",
                    reasonCode = RevocationReasonCode.SECURITY_POLICY,
                    actorId = initiator.id,
                )
                throw IllegalArgumentException("Too many recipient lookup requests. Please try again later.")
            }
        }

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

        val participants = sessionInitiationDto.participants
            .filterNot { p ->
                p.participantType != SharingSessionParticipantType.GROUP &&
                    runCatching { UUID.fromString(p.id) }.getOrNull() == initiator.id
            }
            .map { p ->

            var participantAppUser: AppUser? = null
            var participantGroup: OrganizationGroup? = null
            var participantType = SharingSessionParticipantType.APP_USER

            if (p.participantType == SharingSessionParticipantType.GROUP)
            {
                participantType = SharingSessionParticipantType.APP_USER
                participantGroup = orgGroupService.getById(p.id)
            }
            else
            {
                participantAppUser = appUserService.getById(UUID.fromString(p.id))
            }

            SharingSessionParticipant().apply {
                this.appUser = participantAppUser
                this.organizationGroup = participantGroup
                this.addedDate = Timestamp.from(Instant.now())
                this.participantType = participantType
            }
        }.toMutableList()

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


        participants.forEach {
            it.sharingSession = sharingSession
            entityManager.detach(it)
        }

        val savedSharingSession = sharingSessionRepository.save(sharingSession)

        sendNotifications(
            recipientType = sessionInitiationDto.recipientType!!,
            initiator = initiator,
            recipientAppUser = appUserRecipient,
            recipientOrgGroup = orgGroupRecipient,
            sharingSession = savedSharingSession,
        )

        logger.info("Sharing session initiated ID: ${sharingSession.id}")
        return savedSharingSession
    }

    fun validateSharingSessionFields(
        initiator: AppUser,
        sessionInitiationDto: SharingSessionInitiationDto
    )
    {
        if (sessionInitiationDto.sessionName.isNullOrBlank())
        {
            throw IllegalArgumentException("Session name is required")
        }

        when (sessionInitiationDto.recipientType)
        {
            EMAIL ->
            {
                if ((sessionInitiationDto.recipientEmail?.trim()?.length ?: 0) < 5)
                {
                    throw InvalidEmailException("Recipient email is invalid")
                }

                if (initiator.email.normalizeEmailOrNull() == sessionInitiationDto.recipientEmail.normalizeEmailOrNull())
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

                    val recipientId = UUID.fromString(it)
                    if (recipientId == initiator.id)
                    {
                        throw IllegalArgumentException("Recipient and Initiator cannot be the same")
                    }
                    appUserService.getById(recipientId)
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

        sessionInitiationDto.participants.map {

//            appUserService.getById(UUID.fromString(it.id))
//                ?: throw AppUserNotFoundException("One of the participants not found")
        }
    }

    fun sendNotifications(
        recipientType: SharingSessionRecipientType,
        initiator: AppUser,
        recipientAppUser: AppUser?,
        recipientOrgGroup: OrganizationGroup?,
        sharingSession: SharingSession,
    )
    {
        val initiatorName = initiator.person?.let { "${it.firstName} ${it.lastName}" } ?: initiator.email
        val documentTitles = sharingSession.documents.map { it.title }
        val sessionIdStr = sharingSession.id.toString()
        val subjectTitle = configurationService.emailSubjectTitle

        val recipientEmails: List<Pair<String, String>> = when (recipientType)
        {
            GROUP -> recipientOrgGroup?.members
                ?.mapNotNull { it.appUser }
                ?.filter { it.id != initiator.id }
                ?.map { it.email to (it.person?.firstName ?: "there") }
                ?: emptyList()
            else -> recipientAppUser
                ?.takeIf { !it.email.isNullOrBlank() }
                ?.let { listOf(it.email to (it.person?.firstName ?: "there")) }
                ?: emptyList()
        }

        val recipientLabel = when (recipientType)
        {
            GROUP -> recipientOrgGroup?.name?.let { "Group: $it" } ?: "Group"
            else -> recipientAppUser?.email ?: "Recipient"
        }

        recipientEmails.forEach { (email, _) ->
            try
            {
                val body = emailTemplateService.renderSharingSessionCreatedRecipientEmail(
                    sessionId = sessionIdStr,
                    sessionName = sharingSession.sessionName.orEmpty(),
                    initiatorName = initiatorName,
                    initiatorOrganization = null,
                    sessionMessage = sharingSession.initialShareMessage,
                    documents = documentTitles,
                )
                emailService.sendEmail(
                    to = email,
                    subject = "$subjectTitle | Document request from $initiatorName",
                    body = body,
                    useHtml = true,
                )
            }
            catch (e: Exception)
            {
                logger.error("Failed to send sharing-session recipient email to {}", email, e)
            }
        }

        try
        {
            val body = emailTemplateService.renderSharingSessionCreatedInitiatorEmail(
                sessionId = sessionIdStr,
                sessionName = sharingSession.sessionName.orEmpty(),
                recipientLabel = recipientLabel,
                documents = documentTitles,
            )
            emailService.sendEmail(
                to = initiator.email,
                subject = "$subjectTitle | Document request sent",
                body = body,
                useHtml = true,
            )
        }
        catch (e: Exception)
        {
            logger.error("Failed to send sharing-session initiator email to {}", initiator.email, e)
        }
    }
}