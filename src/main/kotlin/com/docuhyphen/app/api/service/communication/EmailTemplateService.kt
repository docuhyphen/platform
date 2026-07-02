package com.docuhyphen.app.api.service.communication

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.service.communication.templates.AuthEmailTemplateService
import com.docuhyphen.app.api.service.communication.templates.EmailTemplateRenderer
import com.docuhyphen.app.api.service.communication.templates.OrganizationEmailTemplateService
import com.docuhyphen.app.api.service.communication.templates.RenderedEmailTemplate
import com.docuhyphen.app.api.service.communication.templates.ExchangeEmailTemplateService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class EmailTemplateService @Inject constructor(
    private val renderer: EmailTemplateRenderer,
    private val authTemplates: AuthEmailTemplateService,
    private val organizationTemplates: OrganizationEmailTemplateService,
    private val exchangeTemplates: ExchangeEmailTemplateService,
)
{
    enum class ExchangeStatusEmailAudience
    {
        INITIATOR,
        RECIPIENT,
    }

    fun renderTemplate(templateName: String, model: Map<String, Any>): String =
        renderer.render(templateName, model)

    fun renderSignUpInitiationEmail(email: String, otp: String, confirmationToken: String, expiryMinutes: Long): String =
        authTemplates.renderSignUpInitiationEmail(email, otp, confirmationToken, expiryMinutes)

    fun renderSignUpCompletionEmail(email: String): String =
        authTemplates.renderSignUpCompletionEmail(email)

    fun renderSignUpOtpRegenerationEmail(otp: String, confirmationToken: String, expiryMinutes: Long): String =
        authTemplates.renderSignUpOtpRegenerationEmail(otp, confirmationToken, expiryMinutes)

    fun renderSignInMfaEmail(otp: String, expiryMinutes: Long): String =
        authTemplates.renderSignInMfaEmail(otp, expiryMinutes)

    fun renderStepUpMfaEmail(otp: String, expiryMinutes: Long, actionDescription: String): String =
        authTemplates.renderStepUpMfaEmail(otp, expiryMinutes, actionDescription)

    fun renderSignInMfaResendEmail(otp: String, expiryMinutes: Long): String =
        authTemplates.renderSignInMfaResendEmail(otp, expiryMinutes)

    fun renderOrganizationRegistrationEmail(
        firstName: String,
        lastName: String,
        organizationName: String,
        registrationNumber: String,
        organizationEmail: String?,
        organizationPhone: String?,
    ): String = organizationTemplates.renderOrganizationRegistrationEmail(
        firstName,
        lastName,
        organizationName,
        registrationNumber,
        organizationEmail,
        organizationPhone,
    )

    fun renderOrganizationUpdateEmail(
        organizationName: String,
        updatedFields: List<String>,
        updatedBy: String,
    ): String = organizationTemplates.renderOrganizationUpdateEmail(
        organizationName,
        updatedFields,
        updatedBy,
    )

    fun renderEmailUpdateOldVerificationEmail(newEmail: String, verificationCode: String, expiryMinutes: Long): String =
        authTemplates.renderEmailUpdateOldVerificationEmail(newEmail, verificationCode, expiryMinutes)

    fun renderEmailUpdateNewVerificationEmail(newEmail: String, verificationCode: String, expiryMinutes: Long): String =
        authTemplates.renderEmailUpdateNewVerificationEmail(newEmail, verificationCode, expiryMinutes)

    fun renderEmailUpdateCompletionEmail(oldEmail: String, newEmail: String): String =
        authTemplates.renderEmailUpdateCompletionEmail(oldEmail, newEmail)

    fun renderProfileUpdatedEmail(updatedFields: List<String>): String =
        authTemplates.renderProfileUpdatedEmail(updatedFields)

    fun renderOrganizationMemberAddedEmail(
        firstName: String,
        organizationName: String,
        role: String,
        addedBy: String,
        isNewUser: Boolean,
        temporaryPassword: String? = null,
        temporaryPasswordExpiresAt: String? = null,
    ): String = organizationTemplates.renderOrganizationMemberAddedEmail(
        firstName,
        organizationName,
        role,
        addedBy,
        isNewUser,
        temporaryPassword,
        temporaryPasswordExpiresAt,
    )

    fun renderOrganizationMemberRemovedEmail(
        firstName: String,
        organizationName: String,
        removedBy: String,
    ): String = organizationTemplates.renderOrganizationMemberRemovedEmail(
        firstName,
        organizationName,
        removedBy,
    )

    fun renderOrganizationMemberDeactivatedEmail(
        firstName: String,
        organizationName: String,
        deactivatedBy: String,
    ): String = organizationTemplates.renderOrganizationMemberDeactivatedEmail(
        firstName,
        organizationName,
        deactivatedBy,
    )

    fun renderOrganizationMemberReactivatedEmail(
        firstName: String,
        organizationName: String,
        reactivatedBy: String,
    ): String = organizationTemplates.renderOrganizationMemberReactivatedEmail(
        firstName,
        organizationName,
        reactivatedBy,
    )

    fun renderGroupMemberAddedEmail(
        firstName: String,
        groupName: String,
        organizationName: String,
        addedBy: String,
    ): String = organizationTemplates.renderGroupMemberAddedEmail(
        firstName,
        groupName,
        organizationName,
        addedBy,
    )

    fun renderGroupCreatedEmail(
        firstName: String,
        groupName: String,
        organizationName: String?,
        memberCount: Int,
    ): String = organizationTemplates.renderGroupCreatedEmail(
        firstName,
        groupName,
        organizationName,
        memberCount,
    )

    fun renderGroupMemberRemovedEmail(
        firstName: String,
        groupName: String,
        organizationName: String,
        removedBy: String,
    ): String = organizationTemplates.renderGroupMemberRemovedEmail(
        firstName,
        groupName,
        organizationName,
        removedBy,
    )

    fun renderGroupUpdatedEmail(
        firstName: String,
        groupName: String,
        organizationName: String,
        updatedBy: String,
        updatedFields: List<String>,
    ): String = organizationTemplates.renderGroupUpdatedEmail(
        firstName,
        groupName,
        organizationName,
        updatedBy,
        updatedFields,
    )

    fun renderRoleChangedEmail(
        firstName: String,
        organizationName: String,
        oldRole: String,
        newRole: String,
        changedBy: String,
    ): String = organizationTemplates.renderRoleChangedEmail(
        firstName,
        organizationName,
        oldRole,
        newRole,
        changedBy,
    )

    fun renderExchangeCreatedRecipientEmail(
        exchangeId: String,
        name: String,
        initiatorName: String,
        initiatorOrganization: String?,
        sessionMessage: String?,
        documents: List<String>,
        requireSignIn: Boolean = false,
    ): String = exchangeTemplates.renderExchangeCreatedRecipientEmail(
        exchangeId,
        name,
        initiatorName,
        initiatorOrganization,
        sessionMessage,
        documents,
        requireSignIn,
    )

    fun renderExchangeCreatedInitiatorEmail(
        exchangeId: String,
        name: String,
        recipientLabel: String,
        documents: List<String>,
    ): String = exchangeTemplates.renderExchangeCreatedInitiatorEmail(
        exchangeId,
        name,
        recipientLabel,
        documents,
    )

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
    ): RenderedEmailTemplate? = exchangeTemplates.renderExchangeStatusEmail(
        status,
        audience,
        exchangeId,
        name,
        statusText,
        initiatorLabel,
        recipientLabel,
        documents,
        lastActivity,
        rejectionReason,
        endedAt,
    )

    fun renderExchangeCreatedNoAuthRecipientEmail(
        exchangeId: String,
        name: String,
        initiatorName: String,
        initiatorOrganization: String?,
        sessionMessage: String?,
        documents: List<String>,
        otp: String,
        expiryLabel: String,
    ): RenderedEmailTemplate = exchangeTemplates.renderExchangeCreatedNoAuthRecipientEmail(
        exchangeId,
        name,
        initiatorName,
        initiatorOrganization,
        sessionMessage,
        documents,
        otp,
        expiryLabel,
    )

    fun renderNoAuthExchangeOtpEmail(
        exchangeId: String,
        name: String,
        otp: String,
        expiryMinutes: Long,
        initiatorName: String? = null,
    ): RenderedEmailTemplate = exchangeTemplates.renderNoAuthExchangeOtpEmail(
        exchangeId,
        name,
        otp,
        expiryMinutes,
        initiatorName,
    )

    fun renderNewSignInAlertEmail(
        firstName: String?,
        signInAtIso: String,
        device: String,
        ipAddress: String,
        locationHint: String? = null,
    ): RenderedEmailTemplate = authTemplates.renderNewSignInAlertEmail(
        firstName,
        signInAtIso,
        device,
        ipAddress,
        locationHint,
    )

    fun renderPasswordResetRequestEmail(verificationCode: String, expiryMinutes: Long): String =
        authTemplates.renderPasswordResetRequestEmail(verificationCode, expiryMinutes)

    fun renderPasswordChangedEmail(email: String, changedAt: String): String =
        authTemplates.renderPasswordChangedEmail(email, changedAt)

    fun renderAccountDeletedEmail(email: String, deletedAt: String): String =
        authTemplates.renderAccountDeletedEmail(email, deletedAt)

    // -------------------------------------------------------------------------
    // Internal admin notifications
    // -------------------------------------------------------------------------

    /**
     * Plain-HTML notification sent to the support inbox when a new user completes
     * registration. Intentionally simple - this is an internal operational alert,
     * not a user-facing communication.
     */
    fun renderNewUserRegistrationNotificationEmail(email: String): String = """
        <!DOCTYPE html>
        <html lang="en">
        <body style="font-family:sans-serif;color:#222;max-width:560px;margin:0 auto;padding:24px">
          <h2 style="color:#1a73e8">New User Registration</h2>
          <p>A new user has completed registration on <strong>DocuHyphen</strong>.</p>
          <table style="border-collapse:collapse;width:100%">
            <tr>
              <td style="padding:6px 12px 6px 0;font-weight:bold;white-space:nowrap">Email</td>
              <td style="padding:6px 0">$email</td>
            </tr>
          </table>
        </body>
        </html>
    """.trimIndent()

    /**
     * Plain-HTML notification sent to the sales inbox when a new organization
     * registers. Intentionally simple - this is an internal lead-alert email.
     */
    fun renderNewOrgRegistrationNotificationEmail(
        firstName: String,
        lastName: String,
        orgName: String,
        registrationNumber: String,
        orgEmail: String?,
        orgPhone: String?,
    ): String
    {
        val optionalRows = buildString {
            if (!orgEmail.isNullOrBlank())
                append("""<tr><td style="padding:6px 12px 6px 0;font-weight:bold;white-space:nowrap">Organization email</td><td style="padding:6px 0">$orgEmail</td></tr>""")
            if (!orgPhone.isNullOrBlank())
                append("""<tr><td style="padding:6px 12px 6px 0;font-weight:bold;white-space:nowrap">Phone</td><td style="padding:6px 0">$orgPhone</td></tr>""")
        }
        return """
            <!DOCTYPE html>
            <html lang="en">
            <body style="font-family:sans-serif;color:#222;max-width:560px;margin:0 auto;padding:24px">
              <h2 style="color:#1a73e8">New Organization Registration</h2>
              <p>A new organization has registered on <strong>DocuHyphen</strong> and is awaiting verification.</p>
              <table style="border-collapse:collapse;width:100%">
                <tr>
                  <td style="padding:6px 12px 6px 0;font-weight:bold;white-space:nowrap">Organization</td>
                  <td style="padding:6px 0">$orgName</td>
                </tr>
                <tr>
                  <td style="padding:6px 12px 6px 0;font-weight:bold;white-space:nowrap">Registration number</td>
                  <td style="padding:6px 0">$registrationNumber</td>
                </tr>
                <tr>
                  <td style="padding:6px 12px 6px 0;font-weight:bold;white-space:nowrap">Contact name</td>
                  <td style="padding:6px 0">$firstName $lastName</td>
                </tr>
                $optionalRows
              </table>
            </body>
            </html>
        """.trimIndent()
    }
}

