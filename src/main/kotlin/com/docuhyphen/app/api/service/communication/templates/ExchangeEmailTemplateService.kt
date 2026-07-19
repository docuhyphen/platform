package com.docuhyphen.app.api.service.communication.templates

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
        requireSignIn: Boolean = false,
    ): String
    {
        val exchangeLink = "${configurationService.baseUrl}/exchanges?s=$exchangeId"
        val signUpLink = "${configurationService.baseUrl}/sign-up"
        val model = mutableMapOf<String, Any>(
            "name" to name,
            "initiatorName" to initiatorName,
            "documents" to documents,
            "exchangeLink" to exchangeLink,
            "signUpLink" to signUpLink,
            "requireSignIn" to requireSignIn,
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
        initiatorLabel: String,
        recipientLabel: String,
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
            "initiatorLabel" to initiatorLabel,
            "recipientLabel" to recipientLabel,
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
            ExchangeStatus.RESCINDED -> "exchange-status-rescinded.ftl"
        }

        return RenderedEmailTemplate(
            subject = exchangeStatusSubject(status, audience, name, initiatorLabel),
            body = renderer.render(templateName, model),
        )
    }

    fun renderExchangeCreatedNoAuthRecipientEmail(
        exchangeId: String,
        name: String,
        initiatorName: String,
        initiatorOrganization: String?,
        sessionMessage: String?,
        documents: List<String>,
        otp: String,
        accessToken: String,
        expiryLabel: String,
    ): RenderedEmailTemplate
    {
        val exchangeLink = "${configurationService.baseUrl}/nas?s=$exchangeId&t=$accessToken"
        val model = mutableMapOf<String, Any>(
            "appName" to configurationService.emailSubjectTitle,
            "name" to name,
            "initiatorName" to initiatorName,
            "documents" to documents,
            "verificationCode" to otp,
            "expiryLabel" to expiryLabel,
            "exchangeLink" to exchangeLink,
        )
        if (!initiatorOrganization.isNullOrBlank()) model["initiatorOrganization"] = initiatorOrganization
        if (!sessionMessage.isNullOrBlank()) model["sessionMessage"] = sessionMessage

        return RenderedEmailTemplate(
            subject = "Document request from $initiatorName",
            body = renderer.render("exchange-created-no-auth-recipient.ftl", model),
        )
    }

    fun renderNoAuthExchangeOtpEmail(
        exchangeId: String,
        name: String,
        otp: String,
        accessToken: String,
        expiryMinutes: Long,
        initiatorName: String? = null,
    ): RenderedEmailTemplate
    {
        val exchangeLink = "${configurationService.baseUrl}/nas?s=$exchangeId&t=$accessToken"
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
        initiatorLabel: String,
    ): String
    {
        return when (status)
        {
            ExchangeStatus.INITIATED -> when (audience)
            {
                ExchangeStatusEmailAudience.RECIPIENT -> "Action required: New sharing request from $initiatorLabel"
                ExchangeStatusEmailAudience.INITIATOR -> "Sharing request updated: $name"
            }

            ExchangeStatus.ACCEPTED_STARTED -> when (audience)
            {
                ExchangeStatusEmailAudience.INITIATOR -> "Accepted: Exchange is now active - $name"
                ExchangeStatusEmailAudience.RECIPIENT -> "Confirmed: You have accepted an Exchange request - $name"
            }

            ExchangeStatus.REJECTED -> when (audience)
            {
                ExchangeStatusEmailAudience.INITIATOR -> "Rejected: Exchange request response - $name"
                ExchangeStatusEmailAudience.RECIPIENT -> "Confirmed: You rejected the sharing request - $name"
            }

            ExchangeStatus.ENDED -> when (audience)
            {
                ExchangeStatusEmailAudience.INITIATOR -> "Ended: Exchange closed - $name"
                ExchangeStatusEmailAudience.RECIPIENT -> "Notice: Exchange ended - $name"
            }

            ExchangeStatus.RESCINDED -> when (audience)
            {
                ExchangeStatusEmailAudience.INITIATOR -> "Rescinded: Exchange cancelled - $name"
                ExchangeStatusEmailAudience.RECIPIENT -> "Notice: Exchange was rescinded - $name"
            }
        }
    }
}



