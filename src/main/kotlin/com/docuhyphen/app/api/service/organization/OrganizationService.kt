package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.interceptor.EnforceAdminAction
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.service.auth.UserRoleService
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
    private val authAuditService: AuthAuditService,
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService,
    private val configurationService: ConfigurationService,
    private val userRoleService: UserRoleService,
    private val organizationMembershipService: OrganizationMembershipService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationService::class.java)
    }

    @EnforceAdminAction("ORG_UPDATE")
    @Transactional
    fun updateOrganization(
        organizationId: String?,
        name: String?,
        registrationNumber: String?,
        adminApprovalContext: AdminApprovalContext,
    )
    {
        if (organizationId.isNullOrBlank())
        {
            throw OrganizationNotFoundException("Organization ID cannot be null or blank")
        }
        val targetOrgId = try { UUID.fromString(organizationId) }
            catch (e: IllegalArgumentException) { throw OrganizationNotFoundException("Invalid organization ID") }
        if (authTokenContext.authToken.appUser?.id?.let { userRoleService.isOrgAdminIn(it, targetOrgId) } != true)
        {
            throw UnauthorizedException("User does not have permission to update this organization")
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

    @EnforceAdminAction("ORG_SETTINGS_UPDATE")
    fun enforceAdminSafeguardForSettingsUpdate(organizationId: String?, adminApprovalContext: AdminApprovalContext)
    {
        if (authTokenContext.authToken.appUser?.id?.let { userRoleService.isOrgAdmin(it) } != true)
        {
            throw UnauthorizedException("User does not have permission to update organization settings")
        }

        if (organizationId.isNullOrBlank())
        {
            throw OrganizationNotFoundException("Organization ID cannot be null or blank")
        }
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

        return organizationMembershipService.membersOf(organization.id)
    }

    fun update(organization: Organization)
    {
        organizationRepository.update(organization)
    }

    fun searchDiscoverableForTrustRequests(
        activeOrganizationId: UUID,
        normalizedQuery: String,
        limit: Int,
    ): List<Organization> = organizationRepository.searchDiscoverableForTrustRequests(
        activeOrganizationId,
        normalizedQuery,
        limit,
    )

    private fun sendOrganizationUpdateEmail(organization: Organization, updatedFields: List<String>)
    {
        val currentAppUser = authTokenContext.authToken.appUser ?: return
        val updatedBy = currentAppUser.person?.let { "${it.firstName} ${it.lastName}" } ?: currentAppUser.email

        val emailBody = emailTemplateService.renderOrganizationUpdateEmail(
            organizationName = organization.name,
            updatedFields = updatedFields,
            updatedBy = updatedBy,
        )

        organizationMembershipService.membersOf(organization.id).forEach { appUser ->
            try
            {
                emailService.sendEmail(
                    to = appUser.email,
                    subject = "Organization Updated",
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
        return "id=${organization.id};name=${organization.name};registrationNumber=${organization.registrationNumber};isActive=${organization.isActive};verificationComplete=${organization.verificationComplete};appUsers=${organizationMembershipService.activeMemberCount(organization.id)}"
    }
}

