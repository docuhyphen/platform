package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.model.OrganizationGroupDtoMapper
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.dto.PrincipalGroupDto
import com.docuhyphen.app.api.model.dto.PrincipalGroupMemberDto
import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.model.entity.PrincipalGroupScope
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.organization.PersonalGroupDtoMapper
import com.docuhyphen.app.api.model.organization.PrincipalGroupMemberSpec
import com.docuhyphen.app.api.resource.model.PersonalGroupMemberEntry
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.io.File
import java.util.*

@ApplicationScoped
class PersonalGroupService @Inject constructor(
    private val principalGroupService: PrincipalGroupService,
    private val appUserService: AppUserService,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
)
{
    @Transactional
    fun create(name: String?, description: String?): PrincipalGroupDto
    {
        val owner = requirePrincipal()
        val group = principalGroupService.createPersonalGroup(
            ownerAppUserId = owner.id,
            name = requireName(name),
            description = description,
        )
        return groupView(group)
    }

    fun list(): List<PrincipalGroupDto>
    {
        val principal = requirePrincipal()
        val owned = principalGroupService.findOwnedByUser(principal.id)
        val memberOf = principalGroupService.getActiveGroupIdsForPrincipal(PrincipalKind.USER, principal.id)
            .filter { groupId -> owned.none { it.id == groupId } }
            .mapNotNull { principalGroupService.findById(it) }
            .filter { it.isActive && it.scope == PrincipalGroupScope.PERSONAL }
        return (owned + memberOf).map(::groupView)
    }

    fun get(groupId: String): PrincipalGroupDto
    {
        val id = UUID.fromString(groupId)
        authorize(Action.GROUP_VIEW, id)
        return groupView(requireGroup(id))
    }

    @Transactional
    fun update(groupId: String, name: String?, description: String?): PrincipalGroupDto
    {
        val id = UUID.fromString(groupId)
        authorize(Action.GROUP_MANAGE_MEMBERS, id)
        principalGroupService.renamePersonalGroup(id, requireName(name), description)
        return groupView(requireGroup(id))
    }

    @Transactional
    fun addMembers(groupId: String, entries: List<PersonalGroupMemberEntry>): PrincipalGroupDto
    {
        val id = UUID.fromString(groupId)
        val principal = requirePrincipal()
        authorize(Action.GROUP_MANAGE_MEMBERS, id)

        val specs = entries.map { entry ->
            val principalId = entry.principalId?.let { UUID.fromString(it) }
                ?: throw IllegalArgumentException("principalId is required")
            val kind = runCatching { PrincipalKind.valueOf(entry.principalKind.trim().uppercase()) }
                .getOrElse { throw IllegalArgumentException("Invalid principalKind: ${entry.principalKind}") }
            PrincipalGroupMemberSpec(
                principalId = principalId,
                principalKind = kind,
                groupRole = entry.groupRole,
            )
        }

        principalGroupService.addPersonalMembers(id, specs, principal.id)
        return groupView(requireGroup(id))
    }

    @Transactional
    fun removeMember(groupId: String, principalId: String, principalKind: String?)
    {
        val id = UUID.fromString(groupId)
        val memberId = UUID.fromString(principalId)
        authorize(Action.GROUP_MANAGE_MEMBERS, id)

        val kind = principalKind
            ?.let { runCatching { PrincipalKind.valueOf(it.trim().uppercase()) }.getOrNull() }
            ?: PrincipalKind.USER

        principalGroupService.removePersonalMember(id, kind, memberId)
    }

    @Transactional
    fun delete(groupId: String)
    {
        val id = UUID.fromString(groupId)
        authorize(Action.GROUP_DELETE, id)
        principalGroupService.deletePersonalGroup(id)
    }

    @Transactional
    fun updateIcon(groupId: String, file: File?, contentType: String?): PrincipalGroupDto
    {
        val id = UUID.fromString(groupId)
        authorize(Action.GROUP_MANAGE_MEMBERS, id)
        val iconData = principalGroupService.iconDataUrl(file, contentType)
        return groupView(principalGroupService.replaceIcon(requireGroup(id), iconData))
    }

    @Transactional
    fun removeIcon(groupId: String): PrincipalGroupDto
    {
        val id = UUID.fromString(groupId)
        authorize(Action.GROUP_MANAGE_MEMBERS, id)
        return groupView(principalGroupService.replaceIcon(requireGroup(id), null))
    }

    private fun requireName(name: String?): String =
        name?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Group name is required")

    private fun requireGroup(groupId: UUID): PrincipalGroup =
        principalGroupService.findById(groupId) ?: throw PersonalGroupNotFoundException("Group not found")

    private fun requirePrincipal(): PrincipalRef =
        authorizationContextFactory.currentPrincipal() ?: throw ForbiddenException("Authentication required")

    private fun authorize(action: Action, groupId: UUID)
    {
        val principal = requirePrincipal()
        val decision = authorizationService.authorize(
            principal = principal,
            action = action,
            resource = ResourceRef.group(groupId),
            context = authorizationContextFactory.currentContext(),
        )
        if (decision is Decision.Deny)
        {
            throw ForbiddenException("Not authorized: ${decision.reasonCode}")
        }
    }

    private fun groupView(group: PrincipalGroup): PrincipalGroupDto =
        PersonalGroupDtoMapper.toDto(group, memberViews(group.id))

    private fun memberViews(groupId: UUID): List<PrincipalGroupMemberDto> =
        principalGroupService.activeMembers(groupId).map { member ->
            val user = if (member.principalKind == PrincipalKind.USER) appUserService.getById(member.principalId) else null
            OrganizationGroupDtoMapper.toMemberDto(member, user)
        }
}
