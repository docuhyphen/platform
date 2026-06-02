package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.OrganizationLinkNotFoundException
import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.NotificationDto
import com.docuhyphen.app.api.model.dto.NotificationType
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.LinkStatus
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationSharingSessionLink
import com.docuhyphen.app.api.realtime.RealtimeEventService
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
    private val realtimeEventService: RealtimeEventService,
    private val userRoleService: com.docuhyphen.app.api.service.auth.UserRoleService,
    private val organizationMembershipService: OrganizationMembershipService,
)
{
    fun getOrganizationsForLinking(): List<Organization>
    {
        val appUser = authContext.authToken.appUser
            ?: throw UnauthorizedException("User must be authenticated")

        if (!userRoleService.isOrgAdmin(appUser.id))
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

        if (appUser == null || !userRoleService.isOrgAdmin(appUser.id))
        {
            throw IllegalArgumentException("Only organization administrators can create sharing session links")
        }

        adminActionGuardService.enforce(
            action = "ORG_LINK_CREATE",
            actorId = appUser.id,
            context = adminApprovalContext,
        )

        if (!organizationMembershipService.isMember(appUser.id, requestingOrganization.id))
        {
            throw IllegalArgumentException("App user is not part of the requesting organization")
        }

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

        val appBaseUrl = configurationService.baseUrl
        organizationMembershipService.membersOf(requestedOrganization.id).forEach { orgAppUser ->

            if (userRoleService.isOrgAdminIn(orgAppUser.id, requestedOrganization.id))
            {
                emailService.sendEmail(
                    orgAppUser.email,
                    "${configurationService.emailSubjectTitle} | Paring Request",
                    """
                        <p>You have a new pairing request from ${appUser.person?.firstName} ${appUser.person?.lastName}
                        (${requestingOrganization.name}).</p>
                        <p>Open <a href="$appBaseUrl/settings">$appBaseUrl/settings</a> to review and respond.</p>
                    """.trimIndent(),
                    useHtml = true,
                )
            }
        }

        broadcastOrgPairUpdate(requestedOrganization, "New pairing request from ${requestingOrganization.name}")

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

        if (appUser == null || !userRoleService.isOrgAdmin(appUser.id))
        {
            throw IllegalArgumentException("Only organization administrators can accept or delcine sharing session links")
        }

        adminActionGuardService.enforce(
            action = "ORG_LINK_DECIDE",
            actorId = appUser.id,
            context = adminApprovalContext,
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

        if (linkStatus == LinkStatus.ACCEPTED)
        {
            link.requestingOrganization?.id?.let { orgId ->
                organizationMembershipService.membersOf(orgId).forEach { orgAppUser ->

                if (userRoleService.isOrgAdminIn(orgAppUser.id, orgId))
                {
                    emailService.sendEmail(
                        orgAppUser.email,
                        "${configurationService.emailSubjectTitle} | Paring Request Accepted",
                        """
                            Your paring request to ${link.requestedOrganization?.name} has been accepted. You may now start sharing documents.
                            """.trimIndent()
                    )
                }
            } }
        }
        else if (linkStatus == LinkStatus.REJECTED)
        {
            link.requestingOrganization?.id?.let { orgId ->
                organizationMembershipService.membersOf(orgId).forEach { orgAppUser ->

                if (userRoleService.isOrgAdminIn(orgAppUser.id, orgId))
                {
                    emailService.sendEmail(
                        orgAppUser.email,
                        "${configurationService.emailSubjectTitle} | Paring Request Declined",
                        """
                            Your paring request to ${link.requestedOrganization?.name} was declined${if (!rejectionReason.isNullOrBlank()) " with the reason: $rejectionReason" else ""}.
                            """.trimIndent()
                    )
                }
            } }
        }

        val statusLabel = if (linkStatus == LinkStatus.ACCEPTED) "accepted" else "declined"
        broadcastOrgPairUpdate(
            link.requestingOrganization,
            "Your pairing request to ${link.requestedOrganization?.name} was $statusLabel",
        )

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

        if (appUser == null || !userRoleService.isOrgAdmin(appUser.id))
        {
            throw IllegalArgumentException("Only organization administrators can view sharing session links")
        }

        if (!organizationMembershipService.isMember(appUser.id, organization.id))
        {
            throw IllegalArgumentException("App user is not part of the organization")
        }

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

        if (appUser == null || !userRoleService.isOrgAdmin(appUser.id))
        {
            throw IllegalArgumentException("Only organization administrators can view sharing session links")
        }

        adminActionGuardService.enforce(
            action = "ORG_LINK_DELETE",
            actorId = appUser.id,
            context = adminApprovalContext,
        )

        //ToDO: validate of appUser is part of the requesting or requested organization

        // Notify the *other* organization's admins about the un-pair / cancelled request.
        val otherOrg = if (link.requestingOrganization?.id
                ?.let { organizationMembershipService.isMember(appUser.id, it) } == true)
        {
            link.requestedOrganization
        }
        else
        {
            link.requestingOrganization
        }

        otherOrg?.id?.let { otherOrgId ->
        organizationMembershipService.membersOf(otherOrgId).forEach { orgAppUser ->
            if (userRoleService.isOrgAdminIn(orgAppUser.id, otherOrgId))
            {
                try
                {
                    val subject = if (link.status == LinkStatus.ACCEPTED)
                        "${configurationService.emailSubjectTitle} | Organization pairing ended"
                    else
                        "${configurationService.emailSubjectTitle} | Pairing request cancelled"
                    val body = if (link.status == LinkStatus.ACCEPTED)
                        "Your pairing with ${otherOrg.let { _ -> if (otherOrg.id == link.requestingOrganization?.id) link.requestedOrganization?.name else link.requestingOrganization?.name }} has been ended by the other organization."
                    else
                        "A pairing request from ${if (otherOrg.id == link.requestingOrganization?.id) link.requestedOrganization?.name else link.requestingOrganization?.name} was cancelled."
                    emailService.sendEmail(orgAppUser.email, subject, body)
                }
                catch (e: Exception)
                {
                    // best effort,  don't fail the deLink if notification fails
                }
            }
        } }

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

        val deletionMessage = if (link.status == LinkStatus.ACCEPTED)
            "Pairing with ${if (otherOrg?.id == link.requestingOrganization?.id) link.requestedOrganization?.name else link.requestingOrganization?.name} has ended."
        else
            "Pairing request with ${if (otherOrg?.id == link.requestingOrganization?.id) link.requestedOrganization?.name else link.requestingOrganization?.name} was cancelled."
        broadcastOrgPairUpdate(otherOrg, deletionMessage)
    }

    fun getLinksByCurrentAppUser(): List<OrganizationSharingSessionLink>?
    {
        val appUser = authContext.authToken.appUser

        if (appUser == null || !userRoleService.isOrgAdmin(appUser.id))
        {
            throw UnauthorizedException("Only organization group admins can see/manage organizations pairs")
        }

        val appUserOrg = organizationRepository.findByAppUserIdAndPersonId(appUser.id, appUser.person?.id!!)

        return getLinksByOrganization(appUserOrg?.id.toString())
    }

    private fun broadcastOrgPairUpdate(org: Organization?, message: String)
    {
        val orgId = org?.id ?: return
        organizationMembershipService.membersOf(orgId).forEach { orgAppUser ->
            if (userRoleService.isOrgAdminIn(orgAppUser.id, orgId))
            {
                try
                {
                    realtimeEventService.broadcastNotificationToUser(
                        orgAppUser.id,
                        NotificationDto(
                            id = java.util.UUID.randomUUID().toString(),
                            type = NotificationType.NEW_SESSION,
                            message = message,
                            timestamp = Timestamp.from(Instant.now()),
                            data = mapOf("source" to "org-pair"),
                        ),
                    )
                }
                catch (e: Exception)
                {
                    // best effort
                }
            }
        }
    }

    private fun linkSnapshot(link: OrganizationSharingSessionLink): String
    {
        return "id=${link.id};status=${link.status};requestingOrgId=${link.requestingOrganization?.id};requestedOrgId=${link.requestedOrganization?.id};createdDate=${link.createdDate};linkedDate=${link.linkedDate};rejectedDate=${link.rejectedDate};rejectionReason=${link.rejectionReason};requestingMessage=${link.requestingMessage}"
    }
}