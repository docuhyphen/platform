package com.docuhyphen.app.api.service.communication.templates

import com.docuhyphen.app.api.service.auth.StepUpActionLabelFormatter
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@ApplicationScoped
class AuthEmailTemplateService @Inject constructor(
    private val renderer: EmailTemplateRenderer,
    private val configurationService: ConfigurationService,
)
{
    fun renderSignUpInitiationEmail(email: String, otp: String, confirmationToken: String, expiryMinutes: Long): String
    {
        val encodedToken = URLEncoder.encode(confirmationToken, StandardCharsets.UTF_8)
        val emailConfirmationLink = "${configurationService.baseUrl}/sign-up/email-confirm?token=$encodedToken"

        val model = mapOf(
            "email" to email,
            "verificationCode" to otp,
            "expiryMinutes" to expiryMinutes,
            "confirmationLink" to emailConfirmationLink,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderer.render("sign-up-initiation.ftl", model)
    }

    fun renderSignUpCompletionEmail(email: String): String
    {
        val model = mapOf(
            "email" to email,
            "appName" to configurationService.emailSubjectTitle,
            "appBaseUrl" to configurationService.baseUrl,
        )

        return renderer.render("sign-up-completion.ftl", model)
    }

    fun renderSignUpOtpRegenerationEmail(otp: String, confirmationToken: String, expiryMinutes: Long): String
    {
        val encodedToken = URLEncoder.encode(confirmationToken, StandardCharsets.UTF_8)
        val emailConfirmationLink = "${configurationService.baseUrl}/sign-up/email-confirm?token=$encodedToken"

        val model = mapOf(
            "verificationCode" to otp,
            "expiryMinutes" to expiryMinutes,
            "confirmationLink" to emailConfirmationLink,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderer.render("sign-up-otp-regeneration.ftl", model)
    }

    fun renderSignInMfaEmail(otp: String, expiryMinutes: Long): String
    {
        val model = mapOf(
            "verificationCode" to otp,
            "expiryMinutes" to expiryMinutes,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderer.render("sign-in-email-MFA.ftl", model)
    }

    fun renderStepUpMfaEmail(otp: String, expiryMinutes: Long, actionDescription: String): String
    {
        val model = mapOf(
            "verificationCode" to otp,
            "expiryMinutes" to expiryMinutes,
            "appName" to configurationService.emailSubjectTitle,
            "actionDescription" to (StepUpActionLabelFormatter.labelFor(actionDescription) ?: "complete this action"),
        )

        return renderer.render("step-up-email-MFA.ftl", model)
    }

    fun renderSignInMfaResendEmail(otp: String, expiryMinutes: Long): String
    {
        val model = mapOf(
            "verificationCode" to otp,
            "expiryMinutes" to expiryMinutes,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderer.render("sign-in-email-reMFA.ftl", model)
    }

    fun renderEmailUpdateOldVerificationEmail(newEmail: String, verificationCode: String, expiryMinutes: Long): String
    {
        val model = mapOf(
            "newEmail" to newEmail,
            "verificationCode" to verificationCode,
            "expiryMinutes" to expiryMinutes,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderer.render("email-update-old-verification.ftl", model)
    }

    fun renderEmailUpdateNewVerificationEmail(newEmail: String, verificationCode: String, expiryMinutes: Long): String
    {
        val model = mapOf(
            "newEmail" to newEmail,
            "verificationCode" to verificationCode,
            "expiryMinutes" to expiryMinutes,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderer.render("email-update-new-verification.ftl", model)
    }

    fun renderEmailUpdateCompletionEmail(oldEmail: String, newEmail: String): String
    {
        val model = mapOf(
            "oldEmail" to oldEmail,
            "newEmail" to newEmail,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderer.render("email-update-completion.ftl", model)
    }

    fun renderProfileUpdatedEmail(updatedFields: List<String>): String
    {
        val model = mapOf(
            "updatedFields" to updatedFields,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderer.render("profile-updated.ftl", model)
    }

    fun renderNewSignInAlertEmail(
        firstName: String?,
        signInAtIso: String,
        device: String,
        ipAddress: String,
        locationHint: String? = null,
    ): RenderedEmailTemplate
    {
        val model = mutableMapOf<String, Any>(
            "appName" to configurationService.emailSubjectTitle,
            "firstName" to (firstName?.takeIf { it.isNotBlank() } ?: "there"),
            "signInAtIso" to signInAtIso,
            "device" to device,
            "ipAddress" to ipAddress,
            "securityUrl" to "${configurationService.baseUrl}/settings",
        )

        if (!locationHint.isNullOrBlank())
        {
            model["locationHint"] = locationHint
        }

        return RenderedEmailTemplate(
            subject = "New sign-in to your account",
            body = renderer.render("new-sign-in-alert.ftl", model),
        )
    }

    fun renderPasswordResetRequestEmail(verificationCode: String, expiryMinutes: Long): String
    {
        val model = mapOf(
            "verificationCode" to verificationCode,
            "expiryMinutes" to expiryMinutes,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderer.render("password-reset-request.ftl", model)
    }

    fun renderPasswordChangedEmail(email: String, changedAt: String): String
    {
        val model = mapOf(
            "email" to email,
            "changedAt" to changedAt,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderer.render("password-changed.ftl", model)
    }

    fun renderAccountDeletedEmail(email: String, deletedAt: String): String
    {
        val model = mapOf(
            "email" to email,
            "deletedAt" to deletedAt,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderer.render("account-deleted.ftl", model)
    }
}


