package com.docuhyphen.app.api.service.communication.templates

import com.docuhyphen.app.api.model.entity.SharingSessionStatus
import com.docuhyphen.app.api.service.communication.EmailTemplateService.SharingSessionStatusEmailAudience
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class SharingSessionEmailTemplateService @Inject constructor(
    private val renderer: EmailTemplateRenderer,
    private val configurationService: ConfigurationService,
)
{
    fun renderSharingSessionCreatedRecipientEmail(
        sessionId: String,
        sessionName: String,
        initiatorName: String,
        initiatorOrganization: String?,
        sessionMessage: String?,
        documents: List<String>,
    ): String
    {
        val sessionLink = "${configurationService.baseUrl}/sharing-sessions?s=$sessionId"
        val model = mutableMapOf<String, Any>(
            "sessionName" to sessionName,
            "initiatorName" to initiatorName,
            "documents" to documents,
            "sessionLink" to sessionLink,
            "appName" to configurationService.emailSubjectTitle,
        )
        if (!initiatorOrganization.isNullOrBlank()) model["initiatorOrganization"] = initiatorOrganization
        if (!sessionMessage.isNullOrBlank()) model["sessionMessage"] = sessionMessage

        return renderer.render("sharing-session-created-recipient.ftl", model)
    }

    fun renderSharingSessionCreatedInitiatorEmail(
        sessionId: String,
        sessionName: String,
        recipientLabel: String,
        documents: List<String>,
    ): String
    {
        val sessionLink = "${configurationService.baseUrl}/sharing-sessions?s=$sessionId"
        val model = mapOf(
            "sessionName" to sessionName,
            "recipientLabel" to recipientLabel,
            "documents" to documents,
            "sessionLink" to sessionLink,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderer.render("sharing-session-created-initiator.ftl", model)
    }

    fun renderSharingSessionStatusEmail(
        status: SharingSessionStatus,
        audience: SharingSessionStatusEmailAudience,
        sessionId: String,
        sessionName: String,
        statusText: String,
        initiatorEmail: String,
        recipientEmail: String,
        documents: List<String>,
        lastActivity: String,
        rejectionReason: String? = null,
        endedAt: String? = null,
    ): RenderedEmailTemplate?
    {
        if (status == SharingSessionStatus.INITIATED && audience == SharingSessionStatusEmailAudience.INITIATOR)
        {
            return null
        }

        val model = mutableMapOf<String, Any>(
            "appName" to configurationService.emailSubjectTitle,
            "sessionLink" to "${configurationService.baseUrl}/sharing-sessions?s=$sessionId",
            "sessionId" to sessionId,
            "sessionName" to sessionName,
            "statusText" to statusText,
            "audience" to audience.name,
            "initiatorEmail" to initiatorEmail,
            "recipientEmail" to recipientEmail,
            "documents" to documents,
            "lastActivity" to lastActivity,
        )

        if (!rejectionReason.isNullOrBlank())
        {
            model["rejectionReason"] = rejectionReason
        }
        if (!endedAt.isNullOrBlank())
        {
            model["endedAt"] = endedAt
        }

        val templateName = when (status)
        {
            SharingSessionStatus.INITIATED -> "sharing-session-status-initiated.ftl"
            SharingSessionStatus.ACCEPTED_STARTED -> "sharing-session-status-accepted.ftl"
            SharingSessionStatus.REJECTED -> "sharing-session-status-rejected.ftl"
            SharingSessionStatus.ENDED -> "sharing-session-status-ended.ftl"
        }

        return RenderedEmailTemplate(
            subject = sharingSessionStatusSubject(status, audience, sessionName, initiatorEmail),
            body = renderer.render(templateName, model),
        )
    }

    fun renderNoAuthSharingSessionOtpEmail(
        sessionId: String,
        sessionName: String,
        otp: String,
        expiryMinutes: Long,
        initiatorName: String? = null,
    ): RenderedEmailTemplate
    {
        val sessionLink = "${configurationService.baseUrl}/nas?s=$sessionId"
        val model = mutableMapOf<String, Any>(
            "appName" to configurationService.emailSubjectTitle,
            "sessionName" to sessionName,
            "verificationCode" to otp,
            "expiryMinutes" to expiryMinutes,
            "sessionLink" to sessionLink,
        )

        if (!initiatorName.isNullOrBlank())
        {
            model["initiatorName"] = initiatorName
        }

        return RenderedEmailTemplate(
            subject = "Your verification code for the sharing session",
            body = renderer.render("sharing-session-no-auth-otp.ftl", model),
        )
    }

    private fun sharingSessionStatusSubject(
        status: SharingSessionStatus,
        audience: SharingSessionStatusEmailAudience,
        sessionName: String,
        initiatorEmail: String,
    ): String
    {
        return when (status)
        {
            SharingSessionStatus.INITIATED -> when (audience)
            {
                SharingSessionStatusEmailAudience.RECIPIENT -> "Action required: New sharing request from $initiatorEmail"
                SharingSessionStatusEmailAudience.INITIATOR -> "Sharing request updated: $sessionName"
            }

            SharingSessionStatus.ACCEPTED_STARTED -> when (audience)
            {
                SharingSessionStatusEmailAudience.INITIATOR -> "Accepted: Sharing session is now active - $sessionName"
                SharingSessionStatusEmailAudience.RECIPIENT -> "Confirmed: You accepted the sharing request - $sessionName"
            }

            SharingSessionStatus.REJECTED -> when (audience)
            {
                SharingSessionStatusEmailAudience.INITIATOR -> "Rejected: Sharing request response - $sessionName"
                SharingSessionStatusEmailAudience.RECIPIENT -> "Confirmed: You rejected the sharing request - $sessionName"
            }

            SharingSessionStatus.ENDED -> when (audience)
            {
                SharingSessionStatusEmailAudience.INITIATOR -> "Ended: Sharing session closed - $sessionName"
                SharingSessionStatusEmailAudience.RECIPIENT -> "Notice: Sharing session ended - $sessionName"
            }
        }
    }
}



