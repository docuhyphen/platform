package com.docuhyphen.app.api.service.communication

import com.docuhyphen.app.api.configuration.FreeMarkerConfig
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.io.StringWriter

@ApplicationScoped
class EmailTemplateService @Inject constructor(
    private val freeMarkerConfig: FreeMarkerConfig,
    private val configurationService: ConfigurationService
)
{
    fun renderTemplate(templateName: String, model: Map<String, Any>): String
    {
        val template = freeMarkerConfig.configuration.getTemplate(templateName)
        val writer = StringWriter()
        template.process(model, writer)
        return writer.toString()
    }

    fun renderSignUpInitiationEmail(email: String, otp: String, expiryMinutes: Long): String
    {
        val emailConfirmationLink = "${configurationService.baseUrl}/sign-up/email-confirm?email=${email}&otp=${otp}"

        val model = mapOf(
            "email" to email,
            "verificationCode" to otp,
            "expiryMinutes" to expiryMinutes,
            "confirmationLink" to emailConfirmationLink,
            "appName" to configurationService.emailSubjectTitle
        )

        return renderTemplate("sign-up-initiation.ftl", model)
    }

    fun renderSignUpCompletionEmail(email: String): String
    {
        val appBaseUrl = configurationService.baseUrl

        val model = mapOf(
            "email" to email,
            "appName" to configurationService.emailSubjectTitle,
            "appBaseUrl" to appBaseUrl
        )

        return renderTemplate("sign-up-completion.ftl", model)
    }

    /**
     * Renders OTP regeneration email
     * Sent when user requests a new verification code
     */
    fun renderSignUpOtpRegenerationEmail(otp: String, expiryMinutes: Long): String
    {
        val model = mapOf(
            "verificationCode" to otp,
            "expiryMinutes" to expiryMinutes,
            "appName" to configurationService.emailSubjectTitle
        )

        return renderTemplate("sign-up-otp-regeneration.ftl", model)
    }
}

