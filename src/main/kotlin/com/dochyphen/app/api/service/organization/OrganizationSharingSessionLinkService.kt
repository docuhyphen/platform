package com.dochyphen.app.api.service.organization

import com.dochyphen.app.api.exception.OrganizationLinkNotFoundException
import com.dochyphen.app.api.exception.OrganizationNotFoundException
import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.model.entity.AppUserRole
import com.dochyphen.app.api.model.entity.LinkStatus
import com.dochyphen.app.api.model.entity.OrganizationSharingSessionLink
import com.dochyphen.app.api.repository.OrganizationRepository
import com.dochyphen.app.api.repository.OrganizationSharingSessionLinkRepository
import com.dochyphen.app.api.service.communication.EmailService
import com.dochyphen.app.api.service.config.ConfigurationService
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@RequestScoped
class OrganizationSharingSessionLinkService @Inject constructor(
    private val organizationSharingSessionLinkRepository: OrganizationSharingSessionLinkRepository,
    private val organizationRepository: OrganizationRepository,
    private val authContext: AuthTokenContext,
    private val emailService: EmailService,
    private val configurationService: ConfigurationService,
    private val appUserService: OrganizationService,
)
{
    // - getLinksByRequestingOrganization(organizationId: String): List<OrganizationSharingSessionLink>
    // - getLinksByRequestedOrganization(organizationId: String): List<OrganizationSharingSessionLink>

    fun createLink(
        requestingOrganizationId: String?,
        requestedOrganizationId: String?,
        message: String? = null
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

        requestingOrganization.appUsers.firstOrNull { it -> it.id == appUser.id }
            ?: throw IllegalArgumentException("App user is not part of the requesting organization")

        val link = OrganizationSharingSessionLink().apply {
            this.requestingOrganization = requestingOrganization
            this.requestedOrganization = requestedOrganization
            this.requestingMessage = message
            this.status = LinkStatus.PENDING
        }

        val createdLink = organizationSharingSessionLinkRepository.save(link)

        //ToDo: Send email to the organization admin

        requestedOrganization.appUsers.forEach { orgAppUser ->

            if (orgAppUser.role == AppUserRole.ORG_ADMIN)
            {
                emailService.sendEmail(
                    orgAppUser.email,
                    "${configurationService.getAppEmailSubjectTitle()} | Paring Request",
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
        rejectionReason: String? = null
    ): OrganizationSharingSessionLink
    {
        if (linkId.isNullOrBlank())
        {
            throw OrganizationLinkNotFoundException("Link not found")
        }

        linkStatus ?: throw OrganizationLinkNotFoundException("Link status required to accept or decline")

        val link = organizationSharingSessionLinkRepository.findById(UUID.fromString(linkId))
            ?: throw OrganizationLinkNotFoundException("Link not found")

        if (link.status != LinkStatus.PENDING)
        {
            throw IllegalArgumentException("Link is not in a state that can be accepted or declined")
        }

        val appUser = authContext.authToken.appUser

        if (appUser?.role != AppUserRole.ORG_ADMIN)
        {
            throw IllegalArgumentException("Only organization administrators can accept or delcine sharing session links")
        }

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

        link.requestingOrganization?.appUsers?.forEach { orgAppUser ->

            if (orgAppUser.role == AppUserRole.ORG_ADMIN)
            {
                emailService.sendEmail(
                    orgAppUser.email,
                    "${configurationService.getAppEmailSubjectTitle()} | Paring Request Accepted",
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

    fun deLink(linkId: String?)
    {
        val link = organizationSharingSessionLinkRepository.findById(UUID.fromString(linkId))
            ?: throw OrganizationLinkNotFoundException()

        val appUser = authContext.authToken.appUser

        if (appUser?.role != AppUserRole.ORG_ADMIN)
        {
            throw IllegalArgumentException("Only organization administrators can view sharing session links")
        }

        //ToDO: validate of appUser is part of the requesting or requested organization

        //ToDo: decide whether to send a notification email

        organizationSharingSessionLinkRepository.deleteById(link.id)
    }

    fun getLinksByCurrentAppUser(): List<OrganizationSharingSessionLink>?
    {
        val appUser = authContext.authToken.appUser

        if(appUser?.role != AppUserRole.ORG_ADMIN)
        {
            throw UnauthorizedException("Only organization group admins can see/manage organizations pairs")
        }

        val appUserOrg = organizationRepository.findByAppUserIdAndPersonId(appUser.id, appUser.person?.id!!)

        return getLinksByOrganization(appUserOrg?.id.toString())
    }
}