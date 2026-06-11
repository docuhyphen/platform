package com.docuhyphen.app.api.service.communication

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.service.communication.EmailTemplateService.ExchangeStatusEmailAudience
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class ExchangeEmailTemplateService @Inject constructor(
    private val renderer: EmailTemplateRenderer,
    private val configurationService: ConfigurationService,
)
{
    fun renderExchangeCreatedRecipientEmail(
        exchangeId: String,
        name: String,
        initiatorName: String,
        initiatorOrganization: String?,
        sessionMessage: String?,
        documents: List<String>,
    ): String
    {
        val exchangeLink = "${configurationService.baseUrl}/exchanges?s=$exchangeId"
        val model = mutableMapOf<String, Any>(
            "name" to name,
            "initiatorName" to initiatorName,
            "documents" to documents,
            "exchangeLink" to exchangeLink,
            "appName" to configurationService.emailSubjectTitle,
        )
        if (!initiatorOrganization.isNullOrBlank()) model["initiatorOrganization"] = initiatorOrganization
        if (!sessionMessage.isNullOrBlank()) model["sessionMessage"] = sessionMessage

        return renderer.render("exchange-created-recipient.ftl", model)
    }

    fun renderExchangeCreatedInitiatorEmail(
        exchangeId: String,
        name: String,
        recipientLabel: String,
        documents: List<String>,
    ): String
    {
        val exchangeLink = "${configurationService.baseUrl}/exchanges?s=$exchangeId"
        val model = mapOf(
            "name" to name,
            "recipientLabel" to recipientLabel,
            "documents" to documents,
            "exchangeLink" to exchangeLink,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderer.render("exchange-created-initiator.ftl", model)
    }

    fun renderExchangeStatusEmail(
        status: ExchangeStatus,
        audience: ExchangeStatusEmailAudience,
        exchangeId: String,
        name: String,
        statusText: String,
        initiatorEmail: String,
        recipientEmail: String,
        documents: List<String>,
        lastActivity: String,
        rejectionReason: String? = null,
        endedAt: String? = null,
    ): RenderedEmailTemplate?
    {
        if (status == ExchangeStatus.INITIATED && audience == ExchangeStatusEmailAudience.INITIATOR)
        {
            return null
        }

        val model = mutableMapOf<String, Any>(
            "appName" to configurationService.emailSubjectTitle,
            "exchangeLink" to "${configurationService.baseUrl}/exchanges?s=$exchangeId",
            "exchangeId" to exchangeId,
            "name" to name,
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
            ExchangeStatus.INITIATED -> "exchange-status-initiated.ftl"
            ExchangeStatus.ACCEPTED_STARTED -> "exchange-status-accepted.ftl"
            ExchangeStatus.REJECTED -> "exchange-status-rejected.ftl"
            ExchangeStatus.ENDED -> "exchange-status-ended.ftl"
        }

        return RenderedEmailTemplate(
            subject = exchangeStatusSubject(status, audience, name, initiatorEmail),
            body = renderer.render(templateName, model),
        )
    }

    fun renderNoAuthExchangeOtpEmail(
        exchangeId: String,
        name: String,
        otp: String,
        expiryMinutes: Long,
        initiatorName: String? = null,
    ): RenderedEmailTemplate
    {
        val exchangeLink = "${configurationService.baseUrl}/nas?s=$exchangeId"
        val model = mutableMapOf<String, Any>(
            "appName" to configurationService.emailSubjectTitle,
            "name" to name,
            "verificationCode" to otp,
            "expiryMinutes" to expiryMinutes,
            "exchangeLink" to exchangeLink,
        )

        if (!initiatorName.isNullOrBlank())
        {
            model["initiatorName"] = initiatorName
        }

        return RenderedEmailTemplate(
            subject = "Your verification code for the exchange",
            body = renderer.render("exchange-no-auth-otp.ftl", model),
        )
    }

    private fun exchangeStatusSubject(
        status: ExchangeStatus,
        audience: ExchangeStatusEmailAudience,
        name: String,
        initiatorEmail: String,
    ): String
    {
        return when (status)
        {
            ExchangeStatus.INITIATED -> when (audience)
            {
                ExchangeStatusEmailAudience.RECIPIENT -> "Action required: New sharing request from $initiatorEmail"
                ExchangeStatusEmailAudience.INITIATOR -> "Sharing request updated: $name"
            }

            ExchangeStatus.ACCEPTED_STARTED -> when (audience)
            {
                ExchangeStatusEmailAudience.INITIATOR -> "Accepted: Sharing session is now active - $name"
                ExchangeStatusEmailAudience.RECIPIENT -> "Confirmed: You accepted the sharing request - $name"
            }

            ExchangeStatus.REJECTED -> when (audience)
            {
                ExchangeStatusEmailAudience.INITIATOR -> "Rejected: Sharing request response - $name"
                ExchangeStatusEmailAudience.RECIPIENT -> "Confirmed: You rejected the sharing request - $name"
            }

            ExchangeStatus.ENDED -> when (audience)
            {
                ExchangeStatusEmailAudience.INITIATOR -> "Ended: Sharing session closed - $name"
                ExchangeStatusEmailAudience.RECIPIENT -> "Notice: Sharing session ended - $name"
            }
        }
    }
}

