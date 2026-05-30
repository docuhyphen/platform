package com.docuhyphen.app.api.service.communication

import com.docuhyphen.app.api.model.entity.SharingSessionStatus
import com.docuhyphen.app.api.service.communication.templates.AuthEmailTemplateService
import com.docuhyphen.app.api.service.communication.templates.EmailTemplateRenderer
import com.docuhyphen.app.api.service.communication.templates.OrganizationEmailTemplateService
import com.docuhyphen.app.api.service.communication.templates.RenderedEmailTemplate
import com.docuhyphen.app.api.service.communication.templates.SharingSessionEmailTemplateService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class EmailTemplateService @Inject constructor(
    private val renderer: EmailTemplateRenderer,
    private val authTemplates: AuthEmailTemplateService,
    private val organizationTemplates: OrganizationEmailTemplateService,
    private val sharingSessionTemplates: SharingSessionEmailTemplateService,
)
{
    enum class SharingSessionStatusEmailAudience
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

    fun renderSharingSessionCreatedRecipientEmail(
        sessionId: String,
        sessionName: String,
        initiatorName: String,
        initiatorOrganization: String?,
        sessionMessage: String?,
        documents: List<String>,
    ): String = sharingSessionTemplates.renderSharingSessionCreatedRecipientEmail(
        sessionId,
        sessionName,
        initiatorName,
        initiatorOrganization,
        sessionMessage,
        documents,
    )

    fun renderSharingSessionCreatedInitiatorEmail(
        sessionId: String,
        sessionName: String,
        recipientLabel: String,
        documents: List<String>,
    ): String = sharingSessionTemplates.renderSharingSessionCreatedInitiatorEmail(
        sessionId,
        sessionName,
        recipientLabel,
        documents,
    )

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
    ): RenderedEmailTemplate? = sharingSessionTemplates.renderSharingSessionStatusEmail(
        status,
        audience,
        sessionId,
        sessionName,
        statusText,
        initiatorEmail,
        recipientEmail,
        documents,
        lastActivity,
        rejectionReason,
        endedAt,
    )

    fun renderNoAuthSharingSessionOtpEmail(
        sessionId: String,
        sessionName: String,
        otp: String,
        expiryMinutes: Long,
        initiatorName: String? = null,
    ): RenderedEmailTemplate = sharingSessionTemplates.renderNoAuthSharingSessionOtpEmail(
        sessionId,
        sessionName,
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
}

