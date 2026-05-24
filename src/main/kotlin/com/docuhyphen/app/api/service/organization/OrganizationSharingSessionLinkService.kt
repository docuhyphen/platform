package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.OrganizationLinkNotFoundException
import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUserRole
import com.docuhyphen.app.api.model.entity.LinkStatus
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationSharingSessionLink
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.repository.OrganizationSharingSessionLinkRepository
import com.docuhyphen.app.api.service.auth.AdminActionGuardService
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.config.ConfigurationService
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.toString

@RequestScoped
class OrganizationSharingSessionLinkService @Inject constructor(
    private val organizationSharingSessionLinkRepository: OrganizationSharingSessionLinkRepository,
    private val organizationRepository: OrganizationRepository,
    private val authContext: AuthTokenContext,
    private val emailService: EmailService,
    private val configurationService: ConfigurationService,
    private val appUserService: OrganizationService,
    private val adminActionGuardService: AdminActionGuardService,
    private val authAuditService: AuthAuditService,
)
{
    fun getOrganizationsForLinking(): List<Organization>
    {
        val appUser = authContext.authToken.appUser
            ?: throw UnauthorizedException("User must be authenticated")

        if (appUser.role != AppUserRole.ORG_ADMIN)
        {
            throw UnauthorizedException("Only organization administrators can view organizations for linking")
        }

        // Get current user's organization
        val currentOrganization = organizationRepository.findByAppUserIdAndPersonId(appUser.id, appUser.person?.id!!)
            ?: throw OrganizationNotFoundException("Current user's organization not found")

        // Get all existing links for the current organization
        val existingLinks = getLinksByOrganization(currentOrganization.id.toString())

        // Extract organization IDs that are already linked or pending
        val linkedOrganizationIds = existingLinks?.flatMap { link ->
            listOfNotNull(
                link.requestingOrganization?.id,
                link.requestedOrganization?.id
            )
        }?.toSet() ?: emptySet()

        // Get all organizations using the repository method
        val allOrganizations = organizationRepository.findAll()

        // Filter out current organization and already linked organizations using Kotlin filter
        return allOrganizations.filter { org ->
            org.id != currentOrganization.id && !linkedOrganizationIds.contains(org.id)
        }
    }

    fun createLink(
        requestingOrganizationId: String?,
        requestedOrganizationId: String?,
        message: String? = null,
        adminApprovalContext: AdminApprovalContext,
    ): OrganizationSharingSessionLink
    {
        if (requestedOrganizationId.isNullOrBlank())
        {
            throw IllegalArgumentException("Requested organization ID is required")
        }

        val requestingOrganization = organizationRepository
            .findById(UUID.fromString(requestingOrganizationId))
            ?: throw OrganizationNotFoundException("Requesting organization not found")

        val requestedOrganization = organizationRepository
            .findById(UUID.fromString(requestedOrganizationId))
            ?: throw OrganizationNotFoundException("Requested organization not found")

        val appUser = authContext.authToken.appUser

        if (appUser?.role != AppUserRole.ORG_ADMIN)
        {
            throw IllegalArgumentException("Only organization administrators can create sharing session links")
        }

        adminActionGuardService.enforce(
            action = "ORG_LINK_CREATE",
            actorId = appUser.id,
            context = adminApprovalContext,
            requireDualApproval = false,
        )

        requestingOrganization.appUsers.firstOrNull { it -> it.id == appUser.id }
            ?: throw IllegalArgumentException("App user is not part of the requesting organization")

        val link = OrganizationSharingSessionLink().apply {
            this.requestingOrganization = requestingOrganization
            this.requestedOrganization = requestedOrganization
            this.requestingMessage = message
            this.status = LinkStatus.PENDING
        }

        val createdLink = organizationSharingSessionLinkRepository.save(link)

        authAuditService.emit(
            action = "ORG_LINK_CREATE",
            outcome = "SUCCESS",
            actorId = appUser.id,
            organizationId = requestingOrganization.id,
            requestId = adminApprovalContext.requestId,
            reason = "Organization admin created organization link request",
            beforeSnapshot = null,
            afterSnapshot = linkSnapshot(createdLink),
        )

        //ToDo: Send email to the organization admin

        requestedOrganization.appUsers.forEach { orgAppUser ->

            if (orgAppUser.role == AppUserRole.ORG_ADMIN)
            {
                emailService.sendEmail(
                    orgAppUser.email,
                    "${configurationService.emailSubjectTitle} | Paring Request",
                    """
                        You have a new paring request from ${appUser.person?.firstName} ${appUser.person?.lastName} 
                        (${requestingOrganization.name}).
                        """.trimEnd()
                )
            }
        }

        return createdLink
    }

    fun acceptLink(
        linkId: String?,
        linkStatus: LinkStatus?,
        rejectionReason: String? = null,
        adminApprovalContext: AdminApprovalContext,
    ): OrganizationSharingSessionLink
    {
        if (linkId.isNullOrBlank())
        {
            throw OrganizationLinkNotFoundException("Link not found")
        }

        linkStatus ?: throw OrganizationLinkNotFoundException("Link status required to accept or decline")

        val link = organizationSharingSessionLinkRepository.findById(UUID.fromString(linkId))
            ?: throw OrganizationLinkNotFoundException("Link not found")
        val beforeSnapshot = linkSnapshot(link)

        if (link.status != LinkStatus.PENDING)
        {
            throw IllegalArgumentException("Link is not in a state that can be accepted or declined")
        }

        val appUser = authContext.authToken.appUser

        if (appUser?.role != AppUserRole.ORG_ADMIN)
        {
            throw IllegalArgumentException("Only organization administrators can accept or delcine sharing session links")
        }

        adminActionGuardService.enforce(
            action = "ORG_LINK_DECIDE",
            actorId = appUser.id,
            context = adminApprovalContext,
            requireDualApproval = linkStatus == LinkStatus.REJECTED,
        )

//        link.requestedOrganization?.appUsers?.firstOrNull { it -> it.id == appUser.id }
//            ?: throw IllegalArgumentException("App user is not part of the requested organization")

        link.status = linkStatus

        if (linkStatus == LinkStatus.REJECTED)
        {
            link.rejectionReason = rejectionReason
            link.rejectedDate = Timestamp.from(Instant.now())
        }
        else
        {
            link.linkedDate = Timestamp.from(Instant.now())
        }

        val acceptedLink = organizationSharingSessionLinkRepository.update(link)

        authAuditService.emit(
            action = "ORG_LINK_DECIDE",
            outcome = "SUCCESS",
            actorId = appUser.id,
            organizationId = link.requestedOrganization?.id,
            requestId = adminApprovalContext.requestId,
            reason = "Organization admin changed link status to ${linkStatus.name}",
            beforeSnapshot = beforeSnapshot,
            afterSnapshot = linkSnapshot(acceptedLink),
        )

        link.requestingOrganization?.appUsers?.forEach { orgAppUser ->

            if (orgAppUser.role == AppUserRole.ORG_ADMIN)
            {
                emailService.sendEmail(
                    orgAppUser.email,
                    "${configurationService.emailSubjectTitle} | Paring Request Accepted",
                    """
                        Your paring request from ${appUser.person?.firstName} ${appUser.person?.lastName} 
                        (${link.requestingOrganization?.name}) has been accepted. You may now start Sharing Documents.
                        """.trimEnd()
                )
            }
        }

        return acceptedLink;
    }

    fun getLinksByOrganization(organizationId: String?): List<OrganizationSharingSessionLink>?
    {
        if (organizationId.isNullOrBlank())
        {
            throw IllegalArgumentException("Organization ID is required")
        }

        val organization = organizationRepository.findById(UUID.fromString(organizationId))
            ?: throw OrganizationNotFoundException("Organization not found")

        val appUser = authContext.authToken.appUser

        if (appUser?.role != AppUserRole.ORG_ADMIN)
        {
            throw IllegalArgumentException("Only organization administrators can view sharing session links")
        }

        organization.appUsers.firstOrNull { it -> it.id == appUser.id }
            ?: throw IllegalArgumentException("App user is not part of the organization")

        return with(organizationSharingSessionLinkRepository) {

            findByRequestingOrganization(UUID.fromString(organizationId))
                .plus(findByRequestedOrganization(UUID.fromString(organizationId)))
                .distinctBy { it.id }
        }
    }

    fun deLink(linkId: String?, adminApprovalContext: AdminApprovalContext)
    {
        val link = organizationSharingSessionLinkRepository.findById(UUID.fromString(linkId))
            ?: throw OrganizationLinkNotFoundException()
        val beforeSnapshot = linkSnapshot(link)

        val appUser = authContext.authToken.appUser

        if (appUser?.role != AppUserRole.ORG_ADMIN)
        {
            throw IllegalArgumentException("Only organization administrators can view sharing session links")
        }

        adminActionGuardService.enforce(
            action = "ORG_LINK_DELETE",
            actorId = appUser.id,
            context = adminApprovalContext,
            requireDualApproval = true,
        )

        //ToDO: validate of appUser is part of the requesting or requested organization

        //ToDo: decide whether to send a notification email

        organizationSharingSessionLinkRepository.deleteById(link.id)

        authAuditService.emit(
            action = "ORG_LINK_DELETE",
            outcome = "SUCCESS",
            actorId = appUser.id,
            organizationId = link.requestingOrganization?.id,
            requestId = adminApprovalContext.requestId,
            reason = "Organization admin removed organization link",
            beforeSnapshot = beforeSnapshot,
            afterSnapshot = "deleted",
        )
    }

    fun getLinksByCurrentAppUser(): List<OrganizationSharingSessionLink>?
    {
        val appUser = authContext.authToken.appUser

        if (appUser?.role != AppUserRole.ORG_ADMIN)
        {
            throw UnauthorizedException("Only organization group admins can see/manage organizations pairs")
        }

        val appUserOrg = organizationRepository.findByAppUserIdAndPersonId(appUser.id, appUser.person?.id!!)

        return getLinksByOrganization(appUserOrg?.id.toString())
    }

    private fun linkSnapshot(link: OrganizationSharingSessionLink): String
    {
        return "id=${link.id};status=${link.status};requestingOrgId=${link.requestingOrganization?.id};requestedOrgId=${link.requestedOrganization?.id};createdDate=${link.createdDate};linkedDate=${link.linkedDate};rejectedDate=${link.rejectedDate};rejectionReason=${link.rejectionReason};requestingMessage=${link.requestingMessage}"
    }
}