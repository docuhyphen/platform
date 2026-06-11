package com.docuhyphen.app.api.service.communication

import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.Locale

@ApplicationScoped
class OrganizationEmailTemplateService @Inject constructor(
    private val renderer: EmailTemplateRenderer,
    private val configurationService: ConfigurationService,
)
{
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

        return renderer.render("organization-registration.ftl", model)
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

        return renderer.render("organization-update.ftl", model)
    }

    fun renderOrganizationMemberAddedEmail(
        firstName: String,
        organizationName: String,
        role: String,
        addedBy: String,
        isNewUser: Boolean,
        temporaryPassword: String? = null,
        temporaryPasswordExpiresAt: String? = null,
    ): String
    {
        val model = mapOf(
            "firstName" to firstName,
            "organizationName" to organizationName,
            "role" to toFriendlyRoleLabel(role),
            "addedBy" to addedBy,
            "isNewUser" to isNewUser,
            "temporaryPassword" to (temporaryPassword ?: ""),
            "temporaryPasswordExpiresAt" to (temporaryPasswordExpiresAt ?: ""),
            "appName" to configurationService.emailSubjectTitle,
            "appBaseUrl" to configurationService.baseUrl,
        )

        return renderer.render("organization-member-added.ftl", model)
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

        return renderer.render("organization-member-removed.ftl", model)
    }

    fun renderOrganizationMemberDeactivatedEmail(
        firstName: String,
        organizationName: String,
        deactivatedBy: String,
    ): String
    {
        val model = mapOf(
            "firstName" to firstName,
            "organizationName" to organizationName,
            "deactivatedBy" to deactivatedBy,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderer.render("organization-member-deactivated.ftl", model)
    }

    fun renderOrganizationMemberReactivatedEmail(
        firstName: String,
        organizationName: String,
        reactivatedBy: String,
    ): String
    {
        val model = mapOf(
            "firstName" to firstName,
            "organizationName" to organizationName,
            "reactivatedBy" to reactivatedBy,
            "appName" to configurationService.emailSubjectTitle,
            "appBaseUrl" to configurationService.baseUrl,
        )

        return renderer.render("organization-member-reactivated.ftl", model)
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

        return renderer.render("group-member-added.ftl", model)
    }

    fun renderGroupCreatedEmail(
        firstName: String,
        groupName: String,
        organizationName: String?,
        memberCount: Int,
    ): String
    {
        val model = mutableMapOf(
            "firstName" to firstName,
            "groupName" to groupName,
            "memberCount" to memberCount,
            "appName" to configurationService.emailSubjectTitle,
            "appBaseUrl" to configurationService.baseUrl,
        )
        if (organizationName != null) model["organizationName"] = organizationName

        return renderer.render("group-created.ftl", model)
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

        return renderer.render("group-member-removed.ftl", model)
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

        return renderer.render("group-updated.ftl", model)
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
            "oldRole" to toFriendlyRoleLabel(oldRole),
            "newRole" to toFriendlyRoleLabel(newRole),
            "changedBy" to changedBy,
            "appName" to configurationService.emailSubjectTitle,
        )

        return renderer.render("role-changed.ftl", model)
    }

    private fun toFriendlyRoleLabel(role: String): String
    {
        return when (role.trim().uppercase(Locale.ROOT))
        {
            "ORG_MEMBER" -> "Organization member"
            "ORG_ADMIN" -> "Organization administrator"
            "ORG_GROUP_ADMIN" -> "Organization group administrator"
            "APP_USER" -> "User"
            "PLATFORM_ADMIN" -> "Platform administrator"
            "APPLICATION" -> "Application"
            else -> role
                .trim()
                .lowercase(Locale.ROOT)
                .split("_")
                .filter { it.isNotBlank() }
                .joinToString(" ") { token -> token.replaceFirstChar { c -> c.titlecase(Locale.ROOT) } }
                .ifBlank { "Member" }
        }
    }
}

