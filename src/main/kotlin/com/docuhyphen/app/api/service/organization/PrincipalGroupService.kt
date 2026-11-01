package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.interceptor.EnforceAdminAction
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.organization.PrincipalGroupMemberSpec
import com.docuhyphen.app.api.repository.contactdetails.UserContactRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestGroupRemovalService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.io.File
import java.util.*

@ApplicationScoped
class PrincipalGroupService @Inject constructor(
    private val groupRepository: PrincipalGroupRepository,
    private val memberRepository: PrincipalGroupMemberRepository,
    private val userContactRepository: UserContactRepository,
    private val shareService: ShareService,
    private val requestGroupRemoval: InformationRequestGroupRemovalService,
)
{
    fun getActiveGroupIdsForPrincipal(principalKind: PrincipalKind, principalId: UUID): Set<UUID> =
        memberRepository.findGroupsForPrincipal(principalKind, principalId)
            .map { it.principalGroupId }
            .toSet()

    fun activeMembers(groupId: UUID): List<PrincipalGroupMember> = memberRepository.findActiveMembers(groupId)

    fun findById(groupId: UUID): PrincipalGroup? = groupRepository.findById(groupId)

    fun findOwnedByUser(ownerAppUserId: UUID): List<PrincipalGroup> = groupRepository.findByOwnerUser(ownerAppUserId)

    fun iconDataUrl(file: File?, contentType: String?): String
    {
        if (file == null) throw IllegalArgumentException("No file provided")
        val bytes = file.readBytes()
        if (bytes.isEmpty()) throw IllegalArgumentException("Uploaded file is empty")
        val mime = contentType?.takeIf { it.startsWith("image/") } ?: "image/png"
        return "data:$mime;base64,${Base64.getEncoder().encodeToString(bytes)}"
    }

    fun replaceIcon(group: PrincipalGroup, iconData: String?): PrincipalGroup
    {
        group.iconData = iconData
        return groupRepository.update(group)
    }

    fun upsertOrgGroup(
        groupId: UUID,
        organizationId: UUID,
        name: String,
        description: String? = null,
        externallyPublished: Boolean = false,
        isActive: Boolean = true,
    ): PrincipalGroup
    {
        val existing = groupRepository.findById(groupId)
        val group = (existing ?: PrincipalGroup().apply { id = groupId }).apply {
            this.name = name
            this.description = description
            this.scope = PrincipalGroupScope.ORG
            this.ownerOrganizationId = organizationId
            this.ownerAppUserId = null
            this.externallyPublished = externallyPublished
            this.isActive = isActive
        }
        return if (existing == null) groupRepository.save(group) else groupRepository.update(group)
    }

    fun syncMembers(
        groupId: UUID,
        desiredMembers: List<PrincipalGroupMemberSpec>,
        addedByAppUserId: UUID? = null,
    )
    {
        val desiredById = desiredMembers.associateBy { it.appUserId }
        val existing = memberRepository.findActiveMembers(groupId)
            .filter { it.principalKind == PrincipalKind.USER }
        val existingById = existing.associateBy { it.principalId }

        for (member in existing)
        {
            if (member.principalId !in desiredById)
            {
                member.isActive = false
                memberRepository.update(member)
                shareService.synchronizeGroupMemberAccess(groupId, member.principalKind, member.principalId, false)
            }
        }

        for (spec in desiredMembers)
        {
            val current = existingById[spec.appUserId]
                ?: memberRepository.findMembership(groupId, PrincipalKind.USER, spec.appUserId)
            if (current == null)
            {
                val member = PrincipalGroupMember().apply {
                    this.principalGroupId = groupId
                    this.principalKind = PrincipalKind.USER
                    this.principalId = spec.appUserId
                    this.groupRole = spec.groupRole
                    this.addedByAppUserId = addedByAppUserId
                    this.isActive = true
                }
                memberRepository.save(member)
            }
            else
            {
                current.groupRole = spec.groupRole
                current.isActive = true
                current.addedByAppUserId = addedByAppUserId
                memberRepository.update(current)
            }
            shareService.synchronizeGroupMemberAccess(groupId, spec.principalKind, spec.principalId, true)
        }
    }

    fun deactivateGroup(groupId: UUID)
    {
        requestGroupRemoval.groupRemoved(groupId)
        shareService.revokeGroupAccess(groupId)
        groupRepository.findById(groupId)?.let {
            it.isActive = false
            groupRepository.update(it)
        }
    }

    fun deleteGroup(groupId: UUID)
    {
        requestGroupRemoval.groupRemoved(groupId)
        shareService.revokeGroupAccess(groupId)
        memberRepository.findActiveMembers(groupId).forEach { memberRepository.delete(it) }
        groupRepository.findById(groupId)?.let { groupRepository.delete(it) }
    }

    fun createPersonalGroup(
        ownerAppUserId: UUID,
        name: String,
        description: String? = null,
    ): PrincipalGroup
    {
        require(name.isNotBlank()) { "Group name must not be blank" }

        val group = PrincipalGroup().apply {
            this.name = name.trim()
            this.description = description?.trim()
            this.scope = PrincipalGroupScope.PERSONAL
            this.ownerOrganizationId = null
            this.ownerAppUserId = ownerAppUserId
            this.externallyPublished = false
            this.isActive = true
        }
        val saved = groupRepository.save(group)

        val ownerMember = PrincipalGroupMember().apply {
            this.principalGroupId = saved.id
            this.principalKind = PrincipalKind.USER
            this.principalId = ownerAppUserId
            this.groupRole = PrincipalGroupRoleName.OWNER
            this.addedByAppUserId = ownerAppUserId
            this.isActive = true
        }
        memberRepository.save(ownerMember)

        return saved
    }

    fun renamePersonalGroup(groupId: UUID, name: String, description: String?)
    {
        require(name.isNotBlank()) { "Group name must not be blank" }
        val group = groupRepository.findById(groupId)
            ?: throw IllegalArgumentException("Group not found: $groupId")
        require(group.scope == PrincipalGroupScope.PERSONAL) { "Group $groupId is not a PERSONAL group" }
        group.name = name.trim()
        group.description = description?.trim()
        groupRepository.update(group)
    }

    fun addPersonalMembers(groupId: UUID, members: List<PrincipalGroupMemberSpec>, actorId: UUID)
    {
        val group = groupRepository.findById(groupId)
            ?: throw IllegalArgumentException("Group not found: $groupId")
        require(group.scope == PrincipalGroupScope.PERSONAL) { "Group $groupId is not a PERSONAL group" }

        for (spec in members)
        {
            if (spec.principalKind == PrincipalKind.USER)
            {
                require(userContactRepository.isMutualContact(actorId, spec.principalId)) {
                    "Cannot add user ${spec.principalId}: not a mutual contact"
                }
            }
            require(spec.principalKind == PrincipalKind.USER || spec.principalKind == PrincipalKind.PARTICIPANT) {
                "Only USER and PARTICIPANT principals may be added to a personal group"
            }

            val effectiveRole = if (spec.groupRole == PrincipalGroupRoleName.OWNER)
                PrincipalGroupRoleName.MEMBER
            else spec.groupRole

            val existing = memberRepository.findMembership(groupId, spec.principalKind, spec.principalId)
            if (existing == null)
            {
                val member = PrincipalGroupMember().apply {
                    this.principalGroupId = groupId
                    this.principalKind = spec.principalKind
                    this.principalId = spec.principalId
                    this.groupRole = effectiveRole
                    this.addedByAppUserId = actorId
                    this.isActive = true
                }
                memberRepository.save(member)
            }
            else
            {
                existing.groupRole = effectiveRole
                existing.isActive = true
                existing.addedByAppUserId = actorId
                memberRepository.update(existing)
            }
            shareService.synchronizeGroupMemberAccess(groupId, spec.principalKind, spec.principalId, true)
        }
    }

    fun removePersonalMember(groupId: UUID, principalKind: PrincipalKind, principalId: UUID)
    {
        val member = memberRepository.findMembership(groupId, principalKind, principalId)
            ?: throw IllegalArgumentException("Member not found in group $groupId")
        require(member.groupRole != PrincipalGroupRoleName.OWNER) { "Cannot remove the group owner" }
        member.isActive = false
        memberRepository.update(member)
        shareService.synchronizeGroupMemberAccess(groupId, principalKind, principalId, false)
    }

    @EnforceAdminAction("PERSONAL_GROUP_DELETE")
    @Transactional
    fun deletePersonalGroup(groupId: UUID)
    {
        val group = groupRepository.findById(groupId)
            ?: throw IllegalArgumentException("Group not found: $groupId")
        require(group.scope == PrincipalGroupScope.PERSONAL) { "Group $groupId is not a PERSONAL group" }
        group.isActive = false
        groupRepository.update(group)
        requestGroupRemoval.groupRemoved(groupId)
        shareService.revokeGroupAccess(groupId)
        memberRepository.findActiveMembers(groupId).forEach {
            it.isActive = false
            memberRepository.update(it)
        }
    }

    fun deactivateMember(groupId: UUID, principalKind: PrincipalKind, principalId: UUID)
    {
        memberRepository.findMembership(groupId, principalKind, principalId)?.let {
            it.isActive = false
            memberRepository.update(it)
        }
        shareService.synchronizeGroupMemberAccess(groupId, principalKind, principalId, false)
    }
}
