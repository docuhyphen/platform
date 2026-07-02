package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.OrganizationGroupNotFoundException
import com.docuhyphen.app.api.exception.OrganizationLinkNotFoundException
import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.model.dto.PrincipalGroupDto
import com.docuhyphen.app.api.model.dto.PrincipalGroupMemberDto
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.LinkStatus
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.resourceservice.OrganizationGroupMemberModel
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.repository.OrganizationExchangeLinkRepository
import com.docuhyphen.app.api.repository.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.PrincipalGroupRepository
import com.docuhyphen.app.api.repository.ShareRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.AdminActionGuardService
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Organization group management over the unified `principal_group` model. The legacy
 * `organization_group*` tables are no longer written or read here.
 */
@RequestScoped
class OrganizationGroupService @Inject constructor(
    private val organizationRepository: OrganizationRepository,
    private val authTokenContext: AuthTokenContext,
    private val appUserService: AppUserService,
    private val adminActionGuardService: AdminActionGuardService,
    private val authAuditService: AuthAuditService,
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService,
    private val configurationService: ConfigurationService,
    private val orgLinkRepository: OrganizationExchangeLinkRepository,
    private val principalGroupService: PrincipalGroupService,
    private val principalGroupRepository: PrincipalGroupRepository,
    private val principalGroupMemberRepository: PrincipalGroupMemberRepository,
    private val shareRepository: ShareRepository,
    private val organizationMembershipService: OrganizationMembershipService,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationGroupService::class.java)
    }

    fun getById(groupId: String?): PrincipalGroup? =
        groupId?.let { principalGroupRepository.findById(UUID.fromString(it)) }
            ?: throw IllegalArgumentException("Group id required")

    fun getOrganizationByAppUserIdAndPersonId(appUserId: UUID, personId: UUID): Organization =
        organizationRepository.findByAppUserIdAndPersonId(appUserId, personId)
            ?: throw OrganizationNotFoundException("Organization not found for appUserId: $appUserId and personId: $personId")

    //For app integrations
    fun getOrganizationByAppUserIdAndAppId(appUserId: UUID, appId: UUID): Organization =
        organizationRepository.findByAppUserIdAndAppId(appUserId, appId)
            ?: throw OrganizationNotFoundException("Organization not found for appUserId: $appUserId and appId: $appId")

    fun getOrganizationById(uUID: UUID): Organization? =
        organizationRepository.findById(uUID)
            ?: throw OrganizationNotFoundException("Organization not found for id: $uUID")

    @Transactional
    fun addOrganizationGroup(
        organizationId: String,
        name: String?,
        members: List<OrganizationGroupMemberModel>,
        adminApprovalContext: AdminApprovalContext,
        externallyPublished: Boolean = false,
    )
    {
        val orgId = UUID.fromString(organizationId)
        // Creating a group is an org-level action: no group resource exists yet, so we authorize
        // against the org itself. ORG_MANAGE_MEMBERS requires ORG_ADMIN/ORG_OWNER/ORG_USER_MANAGER
        // in exactly this org, not merely the caller's primary org.
        authorizeOrg(Action.ORG_MANAGE_MEMBERS, orgId)
        adminActionGuardService.enforce(
            action = "ORG_GROUP_ADD",
            actorId = authTokenContext.authToken.appUser?.id,
            context = adminApprovalContext,
            requireStepUp = true,
        )

        val organization = organizationRepository.findById(orgId)
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")

        if (name.isNullOrBlank()) throw IllegalArgumentException("Group name cannot be blank")
        if (members.isEmpty()) throw IllegalArgumentException("Group members cannot be empty")

        if (principalGroupRepository.findByOwnerOrg(orgId).any { it.name.equals(name.trim(), ignoreCase = true) })
        {
            throw IllegalArgumentException("Group name already exists")
        }

        val specs = toSpecs(organization, members)
        val groupId = UUID.randomUUID()
        principalGroupService.upsertOrgGroup(
            groupId = groupId,
            organizationId = orgId,
            name = name.trim(),
            externallyPublished = externallyPublished,
            isActive = true,
        )
        principalGroupService.syncMembers(groupId, specs, authTokenContext.authToken.appUser?.id)

        authAuditService.emit(
            action = "ORG_GROUP_ADD",
            outcome = "SUCCESS",
            actorId = authTokenContext.authToken.appUser?.id,
            organizationId = organization.id,
            requestId = adminApprovalContext.requestId,
            reason = "Organization admin added group",
            beforeSnapshot = null,
            afterSnapshot = groupSnapshot(groupId, name.trim(), true, specs),
        )

        val actorId = authTokenContext.authToken.appUser?.id
        specs.forEach { spec ->
            appUserService.getById(spec.appUserId)?.let { member ->
                if (actorId != null && member.id == actorId)
                {
                    sendGroupCreatedEmail(member, name.trim(), organization.name, specs.size)
                }
                else
                {
                    sendGroupMemberAddedEmail(member, name.trim(), organization.name)
                }
            }
        }
    }

    @Transactional
    fun updateOrganizationGroup(
        organizationId: String?,
        groupId: String?,
        groupName: String?,
        isActive: Boolean,
        members: List<OrganizationGroupMemberModel>,
        adminApprovalContext: AdminApprovalContext,
        externallyPublished: Boolean = false,
    )
    {
        if (organizationId.isNullOrBlank() || groupId.isNullOrBlank())
        {
            throw OrganizationNotFoundException("Organization not found for id: $organizationId")
        }
        if (groupName.isNullOrBlank()) throw IllegalArgumentException("Group name cannot be blank")

        val orgId = UUID.fromString(organizationId)
        val gid = UUID.fromString(groupId)
        // Managing a group's membership/metadata requires GROUP_ADMIN on the group itself,
        // satisfied by an org admin of the group's org or by a group OWNER/MANAGER.
        authorizeGroup(Action.GROUP_MANAGE_MEMBERS, gid)
        adminActionGuardService.enforce(
            action = "ORG_GROUP_UPDATE",
            actorId = authTokenContext.authToken.appUser?.id,
            context = adminApprovalContext,
        )
        val organization = organizationRepository.findById(orgId)
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")
        val group = principalGroupRepository.findById(gid)
            ?: throw OrganizationGroupNotFoundException("Group not found for id: $groupId")

        if (principalGroupRepository.findByOwnerOrg(orgId)
                .any { it.id != gid && it.name.equals(groupName.trim(), ignoreCase = true) })
        {
            throw IllegalArgumentException("Group name already exists in this organization")
        }

        val prevSpecs = currentMemberSpecs(gid)
        val beforeSnapshot = groupSnapshot(gid, group.name, group.isActive, prevSpecs)
        val previousMemberIds = prevSpecs.map { it.appUserId }.toSet()

        val groupUpdateFields = mutableListOf<String>()
        if (group.name != groupName.trim())
        {
            groupUpdateFields.add("Renamed from \"${group.name}\" to \"${groupName.trim()}\"")
        }
        if (group.isActive != isActive)
        {
            groupUpdateFields.add(if (isActive) "Group reactivated" else "Group deactivated")
        }
        if (group.externallyPublished != externallyPublished)
        {
            groupUpdateFields.add(if (externallyPublished) "Made visible to paired orgs" else "Hidden from paired orgs")
        }

        val specs = toSpecs(organization, members)
        val newMemberIds = specs.map { it.appUserId }.toSet()

        principalGroupService.upsertOrgGroup(
            groupId = gid,
            organizationId = orgId,
            name = groupName.trim(),
            externallyPublished = externallyPublished,
            isActive = isActive,
        )
        principalGroupService.syncMembers(gid, specs, authTokenContext.authToken.appUser?.id)

        authAuditService.emit(
            action = "ORG_GROUP_UPDATE",
            outcome = "SUCCESS",
            actorId = authTokenContext.authToken.appUser?.id,
            organizationId = organization.id,
            requestId = adminApprovalContext.requestId,
            reason = "Organization admin updated group",
            beforeSnapshot = beforeSnapshot,
            afterSnapshot = groupSnapshot(gid, groupName.trim(), isActive, specs),
        )

        (newMemberIds - previousMemberIds).forEach { memberId ->
            appUserService.getById(memberId)?.let { sendGroupMemberAddedEmail(it, groupName.trim(), organization.name) }
        }
        (previousMemberIds - newMemberIds).forEach { memberId ->
            appUserService.getById(memberId)?.let { sendGroupMemberRemovedEmail(it, groupName.trim(), organization.name) }
        }
        if (groupUpdateFields.isNotEmpty())
        {
            (previousMemberIds intersect newMemberIds).forEach { memberId ->
                appUserService.getById(memberId)?.let {
                    sendGroupUpdatedEmail(it, groupName.trim(), organization.name, groupUpdateFields)
                }
            }
        }
    }

    @Transactional
    fun deleteOrganizationGroup(organizationId: String?, groupId: String?, adminApprovalContext: AdminApprovalContext)
    {
        if (organizationId.isNullOrBlank() || groupId.isNullOrBlank())
        {
            throw OrganizationNotFoundException("Organization not found for id: $organizationId")
        }

        val gid = UUID.fromString(groupId)
        // Deleting a group requires GROUP_DELETE on the group, satisfied by an org ADMIN/OWNER
        // of the group's org or by a group OWNER (group MANAGER cannot delete).
        authorizeGroup(Action.GROUP_DELETE, gid)
        adminActionGuardService.enforce(
            action = "ORG_GROUP_DELETE",
            actorId = authTokenContext.authToken.appUser?.id,
            context = adminApprovalContext,
        )

        val organization = organizationRepository.findById(UUID.fromString(organizationId))
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")
        val group = principalGroupRepository.findById(gid)
            ?: throw OrganizationGroupNotFoundException("Group not found for id: $groupId")
        val beforeSnapshot = groupSnapshot(gid, group.name, group.isActive, currentMemberSpecs(gid))

        // A group still referenced by an active share is deactivated rather than hard-deleted.
        val linkedToSessions = shareRepository.findActiveForPrincipal(PrincipalKind.PRINCIPAL_GROUP, gid)
            .any { it.resourceType == ResourceType.EXCHANGE }

        if (linkedToSessions)
        {
            logger.info("Group ${group.name} is linked to active shares. Deactivating instead of deleting.")
            principalGroupService.deactivateGroup(gid)
            authAuditService.emit(
                action = "ORG_GROUP_DELETE",
                outcome = "SUCCESS",
                actorId = authTokenContext.authToken.appUser?.id,
                organizationId = organization.id,
                requestId = adminApprovalContext.requestId,
                reason = "Group linked to active shares, deactivated instead of hard delete",
                beforeSnapshot = beforeSnapshot,
                afterSnapshot = groupSnapshot(gid, group.name, false, currentMemberSpecs(gid)),
            )
        }
        else
        {
            principalGroupService.deleteGroup(gid)
            authAuditService.emit(
                action = "ORG_GROUP_DELETE",
                outcome = "SUCCESS",
                actorId = authTokenContext.authToken.appUser?.id,
                organizationId = organization.id,
                requestId = adminApprovalContext.requestId,
                reason = "Organization admin deleted group",
                beforeSnapshot = beforeSnapshot,
                afterSnapshot = "deleted",
            )
        }
    }

    /** Deactivate a user's membership across all of an organization's groups (e.g. on offboarding). */
    @Transactional
    fun removeUserFromOrganizationGroups(organizationId: UUID, appUserId: UUID)
    {
        principalGroupRepository.findByOwnerOrg(organizationId).forEach { group ->
            principalGroupMemberRepository.findMembership(group.id, PrincipalKind.USER, appUserId)?.let {
                it.isActive = false
                principalGroupMemberRepository.update(it)
            }
        }
    }

    /** Org groups as role-based [PrincipalGroupDto]s sourced from `principal_group`. */
    fun getOrganizationGroupViews(organizationId: String): List<PrincipalGroupDto>
    {
        val orgId = UUID.fromString(organizationId)
        organizationRepository.findById(orgId)
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")
        return principalGroupRepository.findByOwnerOrg(orgId).map { toGroupView(it) }
    }

    /**
     * Externally-published groups of a paired organization, visible to [currentOrganizationId]
     * only when an ACCEPTED pairing exists. Never enumerates users.
     */
    fun getPublishedGroupViewsForPairedOrganization(
        currentOrganizationId: String,
        pairedOrganizationId: String,
    ): List<PrincipalGroupDto>
    {
        validatePairing(currentOrganizationId, pairedOrganizationId)
        return principalGroupRepository
            .findExternallyPublishedFor(UUID.fromString(pairedOrganizationId))
            .map { toGroupView(it) }
    }

    private fun validatePairing(currentOrganizationId: String, pairedOrganizationId: String)
    {
        if (currentOrganizationId == pairedOrganizationId)
        {
            throw IllegalArgumentException("Paired organization must differ from current organization")
        }

        val currentUUID = UUID.fromString(currentOrganizationId)
        val pairedUUID = UUID.fromString(pairedOrganizationId)

        val currentOrg = organizationRepository.findById(currentUUID)
            ?: throw OrganizationNotFoundException("Current organization not found")
        organizationRepository.findById(pairedUUID)
            ?: throw OrganizationNotFoundException("Paired organization not found")

        val appUser = authTokenContext.authToken.appUser
            ?: throw IllegalArgumentException("Caller must be authenticated")
        if (!organizationMembershipService.isMember(appUser.id, currentUUID))
        {
            throw IllegalArgumentException("Caller does not belong to the current organization")
        }

        (orgLinkRepository.findByRequestingOrganization(currentUUID) +
            orgLinkRepository.findByRequestedOrganization(currentUUID))
            .firstOrNull { l ->
                (l.requestingOrganization?.id == pairedUUID || l.requestedOrganization?.id == pairedUUID) &&
                    l.status == LinkStatus.ACCEPTED
            }
            ?: throw OrganizationLinkNotFoundException("No active pairing with the requested organization")
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    /**
     * Authorize the caller for [action] against an organization resource via the unified
     * [AuthorizationService]. Org-level actions (e.g. group creation) authorize on the org
     * itself so role resolution uses the caller's membership grants in that specific org.
     */
    private fun authorizeOrg(action: Action, organizationId: UUID)
    {
        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw IllegalArgumentException("Authentication required to manage organization groups")
        val decision = authorizationService.authorize(
            principal = principal,
            action = action,
            resource = ResourceRef.organization(organizationId),
            context = authorizationContextFactory.currentContext(),
        )
        if (decision is Decision.Deny)
        {
            throw IllegalArgumentException("User does not have permission to manage groups")
        }
    }

    /**
     * Authorize the caller for [action] on the given group via the unified
     * [AuthorizationService]. The membership-to-grant bridge resolves both the caller's org role
     * (org admins of the group's org) and their group role (OWNER/MANAGER).
     */
    private fun authorizeGroup(action: Action, groupId: UUID)
    {
        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw IllegalArgumentException("Authentication required to manage groups")
        val decision = authorizationService.authorize(
            principal = principal,
            action = action,
            resource = ResourceRef.group(groupId),
            context = authorizationContextFactory.currentContext(),
        )
        if (decision is Decision.Deny)
        {
            throw IllegalArgumentException("User does not have permission to manage this group")
        }
    }

    private fun toSpecs(
        organization: Organization,
        members: List<OrganizationGroupMemberModel>,
    ): List<PrincipalGroupService.GroupMemberSpec> =
        members.map { member ->
            val appUserId = UUID.fromString(member.appUserId)
            if (!organizationMembershipService.isMember(appUserId, organization.id))
            {
                throw IllegalArgumentException("Member does not belong to the organization")
            }
            appUserService.getById(appUserId)
                ?: throw IllegalArgumentException("Member not found for id: ${member.appUserId}")
            PrincipalGroupService.GroupMemberSpec(principalId = appUserId, groupRole = member.groupRole)
        }

    private fun currentMemberSpecs(groupId: UUID): List<PrincipalGroupService.GroupMemberSpec> =
        principalGroupMemberRepository.findActiveMembers(groupId)
            .filter { it.principalKind == PrincipalKind.USER }
            .map { PrincipalGroupService.GroupMemberSpec(principalId = it.principalId, groupRole = it.groupRole) }

    private fun toGroupView(group: PrincipalGroup): PrincipalGroupDto
    {
        val members = principalGroupMemberRepository.findActiveMembers(group.id).map { member ->
            val user = if (member.principalKind == PrincipalKind.USER)
                appUserService.getById(member.principalId)?.let { DetailedEntityToDtoTransformer.toDto(it) }
            else null
            PrincipalGroupMemberDto(user = user, groupRole = member.groupRole)
        }
        return PrincipalGroupDto(
            id = group.id,
            createdDate = group.createdDate,
            isActive = group.isActive,
            name = group.name,
            scope = group.scope.name,
            externallyPublished = group.externallyPublished,
            iconUrl = group.iconData,
            members = members,
        )
    }

    private fun actorLabel(): String
    {
        val actor = authTokenContext.authToken.appUser ?: return "an administrator"
        return actor.person?.let { "${it.firstName} ${it.lastName}" } ?: actor.email
    }

    private fun sendGroupCreatedEmail(appUser: AppUser, groupName: String, organizationName: String, memberCount: Int)
    {
        try
        {
            val body = emailTemplateService.renderGroupCreatedEmail(
                firstName = appUser.person?.firstName ?: "there",
                groupName = groupName,
                organizationName = organizationName,
                memberCount = memberCount,
            )
            emailService.sendEmail(
                to = appUser.email,
                subject = "${configurationService.emailSubjectTitle} | Group \"$groupName\" created",
                body = body,
                useHtml = true,
            )
        }
        catch (e: Exception)
        {
            logger.error("Failed to send group-created email to {}", appUser.email, e)
        }
    }

    private fun sendGroupMemberAddedEmail(appUser: AppUser, groupName: String, organizationName: String)
    {
        try
        {
            val body = emailTemplateService.renderGroupMemberAddedEmail(
                firstName = appUser.person?.firstName ?: "there",
                groupName = groupName,
                organizationName = organizationName,
                addedBy = actorLabel(),
            )
            emailService.sendEmail(
                to = appUser.email,
                subject = "${configurationService.emailSubjectTitle} | Added to $groupName",
                body = body,
                useHtml = true,
            )
        }
        catch (e: Exception)
        {
            logger.error("Failed to send group-member-added email to {}", appUser.email, e)
        }
    }

    private fun sendGroupMemberRemovedEmail(appUser: AppUser, groupName: String, organizationName: String)
    {
        try
        {
            val body = emailTemplateService.renderGroupMemberRemovedEmail(
                firstName = appUser.person?.firstName ?: "there",
                groupName = groupName,
                organizationName = organizationName,
                removedBy = actorLabel(),
            )
            emailService.sendEmail(
                to = appUser.email,
                subject = "${configurationService.emailSubjectTitle} | Removed from $groupName",
                body = body,
                useHtml = true,
            )
        }
        catch (e: Exception)
        {
            logger.error("Failed to send group-member-removed email to {}", appUser.email, e)
        }
    }

    private fun sendGroupUpdatedEmail(
        appUser: AppUser,
        groupName: String,
        organizationName: String,
        updatedFields: List<String>,
    )
    {
        try
        {
            val body = emailTemplateService.renderGroupUpdatedEmail(
                firstName = appUser.person?.firstName ?: "there",
                groupName = groupName,
                organizationName = organizationName,
                updatedBy = actorLabel(),
                updatedFields = updatedFields,
            )
            emailService.sendEmail(
                to = appUser.email,
                subject = "${configurationService.emailSubjectTitle} | Group updated",
                body = body,
                useHtml = true,
            )
        }
        catch (e: Exception)
        {
            logger.error("Failed to send group-updated email to {}", appUser.email, e)
        }
    }

    private fun groupSnapshot(
        groupId: UUID,
        name: String,
        isActive: Boolean,
        members: List<PrincipalGroupService.GroupMemberSpec>,
    ): String
    {
        val m = members
            .map { "member=${it.appUserId}:role=${it.groupRole}" }
            .sorted()
            .joinToString("|")
        return "id=$groupId;name=$name;isActive=$isActive;members=[$m]"
    }
}
