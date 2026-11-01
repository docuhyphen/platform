package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.OrganizationGroupNotFoundException
import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.interceptor.EnforceAdminAction
import com.docuhyphen.app.api.model.OrganizationGroupDtoMapper
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.dto.PrincipalGroupDto
import com.docuhyphen.app.api.model.dto.PrincipalGroupMemberDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.organization.PrincipalGroupMemberSpec
import com.docuhyphen.app.api.model.resourceservice.OrganizationGroupMemberModel
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.repository.organization.OrganizationRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.subscription.OrganizationFeatureSubscriptionGuard
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.user.AppUserService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

@RequestScoped
class OrganizationGroupService @Inject constructor(
    private val organizationRepository: OrganizationRepository,
    private val authTokenContext: AuthTokenContext,
    private val appUserService: AppUserService,
    private val authAuditService: AuthAuditService,
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService,
    private val configurationService: ConfigurationService,
    private val principalGroupService: PrincipalGroupService,
    private val principalGroupRepository: PrincipalGroupRepository,
    private val principalGroupMemberRepository: PrincipalGroupMemberRepository,
    private val shareRepository: ShareRepository,
    private val organizationMembershipService: OrganizationMembershipService,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val subscriptionGuard: OrganizationFeatureSubscriptionGuard,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationGroupService::class.java)
    }

    fun getById(groupId: String?): PrincipalGroup? =
        groupId?.let { principalGroupRepository.findById(UUID.fromString(it)) }
            ?: throw IllegalArgumentException("Group id required")

    fun getPublishedExchangeGroups(organizationId: UUID): List<PrincipalGroup> =
        principalGroupRepository.findExternallyPublishedFor(organizationId)

    fun getPublishedExchangeGroup(organizationId: UUID, groupId: UUID): PrincipalGroup? =
        principalGroupRepository.findExternallyPublishedFor(organizationId)
            .firstOrNull { it.id == groupId }

    fun isActiveOwnerOrManager(groupId: UUID, appUserId: UUID): Boolean
    {
        val group = principalGroupRepository.findById(groupId)
            ?: return false
        if (!group.isActive)
        {
            return false
        }
        return principalGroupMemberRepository.findActiveMembers(groupId)
            .any {
                it.principalKind == PrincipalKind.USER &&
                        it.principalId == appUserId &&
                        (it.groupRole == PrincipalGroupRoleName.OWNER ||
                                it.groupRole == PrincipalGroupRoleName.MANAGER)
            }
    }

    fun isActiveDecisionMaker(groupId: UUID, appUserId: UUID): Boolean
    {
        val group = principalGroupRepository.findById(groupId)
            ?: return false
        if (!group.isActive)
        {
            return false
        }
        val activeMembers = principalGroupMemberRepository.findActiveMembers(groupId)
        return if (group.scope == PrincipalGroupScope.PERSONAL)
        {
            activeMembers.any {
                it.principalKind == PrincipalKind.USER && it.principalId == appUserId
            }
        }
        else
        {
            activeMembers.any {
                it.principalKind == PrincipalKind.USER &&
                        it.principalId == appUserId &&
                        (it.groupRole == PrincipalGroupRoleName.OWNER ||
                                it.groupRole == PrincipalGroupRoleName.MANAGER)
            }
        }
    }

    fun activeOwnerOrManagerUserIds(groupId: UUID): Set<UUID>
    {
        val group = principalGroupRepository.findById(groupId)
            ?: return emptySet()
        if (!group.isActive)
        {
            return emptySet()
        }
        return principalGroupMemberRepository.findActiveMembers(groupId)
            .asSequence()
            .filter { it.principalKind == PrincipalKind.USER }
            .filter {
                it.groupRole == PrincipalGroupRoleName.OWNER ||
                        it.groupRole == PrincipalGroupRoleName.MANAGER
            }
            .map { it.principalId }
            .toSet()
    }

    fun activeUserIds(groupId: UUID): Set<UUID> =
        principalGroupMemberRepository.findActiveMembers(groupId)
            .asSequence()
            .filter { it.principalKind == PrincipalKind.USER }
            .map { it.principalId }
            .toSet()

    fun getOrganizationByAppUserIdAndPersonId(appUserId: UUID, personId: UUID): Organization =
        organizationRepository.findByAppUserIdAndPersonId(appUserId, personId)
            ?: throw OrganizationNotFoundException("Organization not found for appUserId: $appUserId and personId: $personId")

    fun getOrganizationByAppUserIdAndAppId(appUserId: UUID, appId: UUID): Organization =
        organizationRepository.findByAppUserIdAndAppId(appUserId, appId)
            ?: throw OrganizationNotFoundException("Organization not found for appUserId: $appUserId and appId: $appId")

    fun getOrganizationById(uUID: UUID): Organization? =
        organizationRepository.findById(uUID)
            ?: throw OrganizationNotFoundException("Organization not found for id: $uUID")

    @EnforceAdminAction("ORG_GROUP_ADD")
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
        authorizeOrg(Action.ORG_MANAGE_MEMBERS, orgId)
        subscriptionGuard.requireMutation(orgId, PlanFeature.ORGANIZATION_ADMINISTRATION)

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

    @EnforceAdminAction("ORG_GROUP_UPDATE")
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
        authorizeGroup(Action.GROUP_MANAGE_MEMBERS, gid)
        subscriptionGuard.requireMutation(orgId, PlanFeature.ORGANIZATION_ADMINISTRATION)
        val organization = organizationRepository.findById(orgId)
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")
        val group = principalGroupRepository.findById(gid)
            ?: throw OrganizationGroupNotFoundException("Group not found for id: $groupId")

        if (principalGroupRepository.findByOwnerOrg(orgId)
                .any { it.id != gid && it.name.equals(groupName.trim(), ignoreCase = true) }
        )
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
            groupUpdateFields.add(if (externallyPublished) "Made visible to trusted organizations" else "Hidden from trusted organizations")
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
            appUserService.getById(memberId)
                ?.let { sendGroupMemberRemovedEmail(it, groupName.trim(), organization.name) }
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

    @EnforceAdminAction("ORG_GROUP_DELETE")
    @Transactional
    fun deleteOrganizationGroup(organizationId: String?, groupId: String?, adminApprovalContext: AdminApprovalContext)
    {
        if (organizationId.isNullOrBlank() || groupId.isNullOrBlank())
        {
            throw OrganizationNotFoundException("Organization not found for id: $organizationId")
        }

        val gid = UUID.fromString(groupId)
        authorizeGroup(Action.GROUP_DELETE, gid)

        val orgId = UUID.fromString(organizationId)
        subscriptionGuard.requireMutation(orgId, PlanFeature.ORGANIZATION_ADMINISTRATION)

        val organization = organizationRepository.findById(orgId)
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")
        val group = principalGroupRepository.findById(gid)
            ?: throw OrganizationGroupNotFoundException("Group not found for id: $groupId")
        val beforeSnapshot = groupSnapshot(gid, group.name, group.isActive, currentMemberSpecs(gid))

        val linkedToActiveShares = shareRepository.findActiveForPrincipal(PrincipalKind.PRINCIPAL_GROUP, gid)
            .any { it.resourceType == ResourceType.EXCHANGE || it.resourceType == ResourceType.INFORMATION_REQUEST }

        if (linkedToActiveShares)
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

    @Transactional
    fun removeUserFromOrganizationGroups(organizationId: UUID, appUserId: UUID)
    {
        principalGroupRepository.findByOwnerOrg(organizationId).forEach { group ->
            principalGroupService.deactivateMember(group.id, PrincipalKind.USER, appUserId)
        }
    }

    @Transactional
    fun updateOrganizationGroupIcon(
        organizationId: String,
        groupId: String,
        file: File?,
        contentType: String?,
    ): PrincipalGroupDto?
    {
        val iconData = principalGroupService.iconDataUrl(file, contentType)
        return replaceOrganizationGroupIcon(organizationId, groupId, iconData)
    }

    @Transactional
    fun removeOrganizationGroupIcon(organizationId: String, groupId: String): PrincipalGroupDto? =
        replaceOrganizationGroupIcon(organizationId, groupId, null)

    private fun replaceOrganizationGroupIcon(organizationId: String, groupId: String, iconData: String?): PrincipalGroupDto?
    {
        val gid = UUID.fromString(groupId)
        val group = principalGroupRepository.findById(gid)
            ?: throw OrganizationGroupNotFoundException("Group not found")
        principalGroupService.replaceIcon(group, iconData)
        return getOrganizationGroupViews(organizationId).find { it.id == gid }
    }

    fun getOrganizationGroupViews(organizationId: String): List<PrincipalGroupDto>
    {
        val orgId = UUID.fromString(organizationId)
        organizationRepository.findById(orgId)
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")
        return principalGroupRepository.findByOwnerOrg(orgId).map { group ->
            OrganizationGroupDtoMapper.toDto(group, activeMemberDtos(group.id))
        }
    }

    private fun activeMemberDtos(groupId: UUID): List<PrincipalGroupMemberDto> =
        principalGroupMemberRepository.findActiveMembers(groupId).map { member ->
            val user = if (member.principalKind == PrincipalKind.USER) appUserService.getById(member.principalId) else null
            OrganizationGroupDtoMapper.toMemberDto(member, user)
        }

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
    ): List<PrincipalGroupMemberSpec> =
        members.map { member ->
            val appUserId = UUID.fromString(member.appUserId)
            if (!organizationMembershipService.isMember(appUserId, organization.id))
            {
                throw IllegalArgumentException("Member does not belong to the organization")
            }
            appUserService.getById(appUserId)
                ?: throw IllegalArgumentException("Member not found for id: ${member.appUserId}")
            PrincipalGroupMemberSpec(principalId = appUserId, groupRole = member.groupRole)
        }

    private fun currentMemberSpecs(groupId: UUID): List<PrincipalGroupMemberSpec> =
        principalGroupMemberRepository.findActiveMembers(groupId)
            .filter { it.principalKind == PrincipalKind.USER }
            .map { PrincipalGroupMemberSpec(principalId = it.principalId, groupRole = it.groupRole) }

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
                subject = "Group \"$groupName\" created",
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
                subject = "Added to $groupName",
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
                subject = "Removed from $groupName",
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
                subject = "Group updated",
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
        members: List<PrincipalGroupMemberSpec>,
    ): String
    {
        val m = members
            .map { "member=${it.appUserId}:role=${it.groupRole}" }
            .sorted()
            .joinToString("|")
        return "id=$groupId;name=$name;isActive=$isActive;members=[$m]"
    }
}
