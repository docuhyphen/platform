package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AppUserRole.ORG_ADMIN
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationGroup
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.service.auth.AdminActionGuardService
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.config.ConfigurationService
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.util.*

@RequestScoped
class OrganizationService @Inject constructor(
    private val organizationGroupService: OrganizationGroupService,
    private val authTokenContext: AuthTokenContext,
    private val organizationRepository: OrganizationRepository,
    private val adminActionGuardService: AdminActionGuardService,
    private val authAuditService: AuthAuditService,
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService,
    private val configurationService: ConfigurationService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationService::class.java)
    }

    @Transactional
    fun updateOrganization(
        organizationId: String?,
        name: String?,
        registrationNumber: String?,
        adminApprovalContext: AdminApprovalContext,
    )
    {
        if (authTokenContext.authToken.appUser?.role != ORG_ADMIN)
        {
            throw UnauthorizedException("User does not have permission to update organizations")
        }

        adminActionGuardService.enforce(
            action = "ORG_UPDATE",
            actorId = authTokenContext.authToken.appUser?.id,
            context = adminApprovalContext,
            requireDualApproval = !registrationNumber.isNullOrBlank(),
        )

        if (organizationId.isNullOrBlank())
        {
            throw OrganizationNotFoundException("Organization ID cannot be null or blank")
        }

        val organization = organizationGroupService.getOrganizationById(UUID.fromString(organizationId))
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")
        val beforeSnapshot = organizationSnapshot(organization)

        val updatedFields = mutableListOf<String>()

        if (!name.isNullOrBlank() && name != organization.name)
        {
            updatedFields.add("Name changed from \"${organization.name}\" to \"$name\"")
            organization.name = name
        }

        if (!registrationNumber.isNullOrBlank() && registrationNumber != organization.registrationNumber)
        {
            updatedFields.add("Registration number updated")
            organization.registrationNumber = registrationNumber
        }

        organizationRepository.update(organization)

        authAuditService.emit(
            action = "ORG_UPDATE",
            outcome = "SUCCESS",
            actorId = authTokenContext.authToken.appUser?.id,
            organizationId = organization.id,
            requestId = adminApprovalContext.requestId,
            reason = if (updatedFields.isEmpty()) "Organization update requested with no effective field changes" else updatedFields.joinToString("; "),
            beforeSnapshot = beforeSnapshot,
            afterSnapshot = organizationSnapshot(organization),
        )

        if (updatedFields.isNotEmpty())
        {
            sendOrganizationUpdateEmail(organization, updatedFields)
        }
    }

    fun enforceAdminSafeguardForSettingsUpdate(organizationId: String?, adminApprovalContext: AdminApprovalContext)
    {
        if (authTokenContext.authToken.appUser?.role != ORG_ADMIN)
        {
            throw UnauthorizedException("User does not have permission to update organization settings")
        }

        if (organizationId.isNullOrBlank())
        {
            throw OrganizationNotFoundException("Organization ID cannot be null or blank")
        }

        adminActionGuardService.enforce(
            action = "ORG_SETTINGS_UPDATE",
            actorId = authTokenContext.authToken.appUser?.id,
            context = adminApprovalContext,
            requireDualApproval = true,
        )
    }

    fun getOrganizationById(organizationId: UUID): Organization
    {
        return organizationRepository.findById(organizationId)
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")
    }

    fun getAppUsers(organizationId: String?): List<AppUser>
    {
        if (organizationId.isNullOrBlank())
        {
            throw OrganizationNotFoundException("Organization ID cannot be null or blank")
        }

        val organization = organizationGroupService.getOrganizationById(UUID.fromString(organizationId))
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")

        return organization.appUsers
    }

    fun update(organization: Organization)
    {
        organizationRepository.update(organization)
    }

    fun getLinkedOrganizations(includePublic: Boolean): List<Organization>
    {
        val currentAppUser = authTokenContext.authToken.appUser
        val currentAppUserPerson = authTokenContext.authToken.appUser?.person!!

        val currentAppUserOrg = organizationRepository.findByAppUserIdAndPersonId(
            currentAppUser?.id!!,
            currentAppUserPerson.id
        )

        return organizationRepository.getLinkedOrganizations(currentAppUserOrg?.id!!, includePublic)
    }

    fun getLinkedOrganizationsAppUsers(organizationId: String?): List<AppUser>
    {
        val currentAppUser = authTokenContext.authToken.appUser

        return getAppUsers(organizationId)
    }

    fun getLinkedOrganizationsGroups(organizationId: String?): List<OrganizationGroup>
    {
        if (organizationId.isNullOrBlank())
        {
            throw OrganizationNotFoundException("organization is required")
        }

        val currentAppUser = authTokenContext.authToken.appUser
            ?: throw OrganizationNotFoundException("Caller must be authenticated")
        val currentOrg = organizationRepository.findByAppUserIdAndPersonId(
            currentAppUser.id, currentAppUser.person?.id!!,
        ) ?: throw OrganizationNotFoundException("Caller has no organization")

        // v1 trusted-org visibility: never enumerate users; expose only the paired org's
        // explicitly published groups. Pairing must be ACCEPTED. See OrganizationGroupService
        // for the link check.
        return organizationGroupService.getPublishedGroupsForPairedOrganization(
            currentOrg.id.toString(),
            organizationId,
        )
    }

    private fun sendOrganizationUpdateEmail(organization: Organization, updatedFields: List<String>)
    {
        val currentAppUser = authTokenContext.authToken.appUser ?: return
        val updatedBy = currentAppUser.person?.let { "${it.firstName} ${it.lastName}" } ?: currentAppUser.email

        val emailBody = emailTemplateService.renderOrganizationUpdateEmail(
            organizationName = organization.name,
            updatedFields = updatedFields,
            updatedBy = updatedBy,
        )

        organization.appUsers.forEach { appUser ->
            try
            {
                emailService.sendEmail(
                    to = appUser.email,
                    subject = "${configurationService.emailSubjectTitle} | Organization Updated",
                    body = emailBody,
                    useHtml = true,
                )
            }
            catch (e: Exception)
            {
                logger.error("Failed to send organization update email to {}", appUser.email, e)
            }
        }
    }

    private fun organizationSnapshot(organization: Organization): String
    {
        return "id=${organization.id};name=${organization.name};registrationNumber=${organization.registrationNumber};isActive=${organization.isActive};verificationComplete=${organization.verificationComplete};appUsers=${organization.appUsers.size};groups=${organization.groups.size}"
    }
}

