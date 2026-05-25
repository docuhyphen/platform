package com.docuhyphen.app.api.service.communication

import com.docuhyphen.app.api.configuration.FreeMarkerConfig
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.io.StringWriter
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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

    fun renderSignUpInitiationEmail(email: String, otp: String, confirmationToken: String, expiryMinutes: Long): String
    {
        // The confirmation link uses an opaque single-use token (no email/OTP in URL)
        // so it stays safe in browser history, Referer headers, and proxy logs.
        val encodedToken = URLEncoder.encode(confirmationToken, StandardCharsets.UTF_8)
        val emailConfirmationLink =
            "${configurationService.baseUrl}/sign-up/email-confirm?token=$encodedToken"

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
    fun renderSignUpOtpRegenerationEmail(otp: String, confirmationToken: String, expiryMinutes: Long): String
    {
        val encodedToken = URLEncoder.encode(confirmationToken, StandardCharsets.UTF_8)
        val emailConfirmationLink =
            "${configurationService.baseUrl}/sign-up/email-confirm?token=$encodedToken"

        val model = mapOf(
            "verificationCode" to otp,
            "expiryMinutes" to expiryMinutes,
            "confirmationLink" to emailConfirmationLink,
            "appName" to configurationService.emailSubjectTitle
        )

        return renderTemplate("sign-up-otp-regeneration.ftl", model)
    }

    fun renderSignInMfaEmail(otp: String, expiryMinutes: Long): String
    {
        val model = mapOf(
            "verificationCode" to otp,
            "expiryMinutes" to expiryMinutes,
            "appName" to configurationService.emailSubjectTitle
        )

        return renderTemplate("sign-in-email-MFA.ftl", model)
    }

    fun renderSignInMfaResendEmail(otp: String, expiryMinutes: Long): String
    {
        val model = mapOf(
            "verificationCode" to otp,
            "expiryMinutes" to expiryMinutes,
            "appName" to configurationService.emailSubjectTitle
        )

        return renderTemplate("sign-in-email-reMFA.ftl", model)
    }

    fun renderOrganizationRegistrationEmail(
        firstName: String,
        lastName: String,
        organizationName: String,
        registrationNumber: String,
        organizationEmail: String?,
        organizationPhone: String?,
    ): String
    {
        val model = mutableMapOf<String, Any>(
            "firstName" to firstName,
            "lastName" to lastName,
            "organizationName" to organizationName,
            "registrationNumber" to registrationNumber,
            "appName" to configurationService.emailSubjectTitle,
            "appBaseUrl" to configurationService.baseUrl,
        )

        if (!organizationEmail.isNullOrBlank())
        {
            model["organizationEmail"] = organizationEmail
        }
        if (!organizationPhone.isNullOrBlank())
        {
            model["organizationPhone"] = organizationPhone
        }

        return renderTemplate("organization-registration.ftl", model)
    }

    fun renderOrganizationUpdateEmail(
        organizationName: String,
        updatedFields: List<String>,
        updatedBy: String,
    ): String
    {
        val model = mapOf(
            "organizationName" to organizationName,
            "updatedFields" to updatedFields,
            "updatedBy" to updatedBy,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderTemplate("organization-update.ftl", model)
    }

    fun renderEmailUpdateOldVerificationEmail(newEmail: String, verificationCode: String, expiryMinutes: Long): String
    {
        val model = mapOf(
            "newEmail" to newEmail,
            "verificationCode" to verificationCode,
            "expiryMinutes" to expiryMinutes,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderTemplate("email-update-old-verification.ftl", model)
    }

    fun renderEmailUpdateNewVerificationEmail(newEmail: String, verificationCode: String, expiryMinutes: Long): String
    {
        val model = mapOf(
            "newEmail" to newEmail,
            "verificationCode" to verificationCode,
            "expiryMinutes" to expiryMinutes,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderTemplate("email-update-new-verification.ftl", model)
    }

    fun renderEmailUpdateCompletionEmail(oldEmail: String, newEmail: String): String
    {
        val model = mapOf(
            "oldEmail" to oldEmail,
            "newEmail" to newEmail,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderTemplate("email-update-completion.ftl", model)
    }

    fun renderProfileUpdatedEmail(updatedFields: List<String>): String
    {
        val model = mapOf(
            "updatedFields" to updatedFields,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderTemplate("profile-updated.ftl", model)
    }

    fun renderOrganizationMemberAddedEmail(
        firstName: String,
        organizationName: String,
        role: String,
        addedBy: String,
        isNewUser: Boolean,
    ): String
    {
        val model = mapOf(
            "firstName" to firstName,
            "organizationName" to organizationName,
            "role" to role,
            "addedBy" to addedBy,
            "isNewUser" to isNewUser,
            "appName" to configurationService.emailSubjectTitle,
            "appBaseUrl" to configurationService.baseUrl,
        )

        return renderTemplate("organization-member-added.ftl", model)
    }

    fun renderOrganizationMemberRemovedEmail(
        firstName: String,
        organizationName: String,
        removedBy: String,
    ): String
    {
        val model = mapOf(
            "firstName" to firstName,
            "organizationName" to organizationName,
            "removedBy" to removedBy,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderTemplate("organization-member-removed.ftl", model)
    }

    fun renderGroupMemberAddedEmail(
        firstName: String,
        groupName: String,
        organizationName: String,
        addedBy: String,
    ): String
    {
        val model = mapOf(
            "firstName" to firstName,
            "groupName" to groupName,
            "organizationName" to organizationName,
            "addedBy" to addedBy,
            "appName" to configurationService.emailSubjectTitle,
            "appBaseUrl" to configurationService.baseUrl,
        )

        return renderTemplate("group-member-added.ftl", model)
    }

    fun renderGroupMemberRemovedEmail(
        firstName: String,
        groupName: String,
        organizationName: String,
        removedBy: String,
    ): String
    {
        val model = mapOf(
            "firstName" to firstName,
            "groupName" to groupName,
            "organizationName" to organizationName,
            "removedBy" to removedBy,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderTemplate("group-member-removed.ftl", model)
    }

    fun renderGroupUpdatedEmail(
        firstName: String,
        groupName: String,
        organizationName: String,
        updatedBy: String,
        updatedFields: List<String>,
    ): String
    {
        val model = mapOf(
            "firstName" to firstName,
            "groupName" to groupName,
            "organizationName" to organizationName,
            "updatedBy" to updatedBy,
            "updatedFields" to updatedFields,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderTemplate("group-updated.ftl", model)
    }

    fun renderRoleChangedEmail(
        firstName: String,
        organizationName: String,
        oldRole: String,
        newRole: String,
        changedBy: String,
    ): String
    {
        val model = mapOf(
            "firstName" to firstName,
            "organizationName" to organizationName,
            "oldRole" to oldRole,
            "newRole" to newRole,
            "changedBy" to changedBy,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderTemplate("role-changed.ftl", model)
    }

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

        return renderTemplate("sharing-session-created-recipient.ftl", model)
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

        return renderTemplate("sharing-session-created-initiator.ftl", model)
    }

    fun renderPasswordResetRequestEmail(verificationCode: String, expiryMinutes: Long): String
    {
        val model = mapOf(
            "verificationCode" to verificationCode,
            "expiryMinutes" to expiryMinutes,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderTemplate("password-reset-request.ftl", model)
    }

    fun renderPasswordChangedEmail(email: String, changedAt: String): String
    {
        val model = mapOf(
            "email" to email,
            "changedAt" to changedAt,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderTemplate("password-changed.ftl", model)
    }

    fun renderAccountDeletedEmail(email: String, deletedAt: String): String
    {
        val model = mapOf(
            "email" to email,
            "deletedAt" to deletedAt,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderTemplate("account-deleted.ftl", model)
    }
}

