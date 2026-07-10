package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.interceptor.EnforceAdminAction
import com.docuhyphen.app.api.model.entity.PrincipalGroupRoleName
import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.model.entity.PrincipalGroupMember
import com.docuhyphen.app.api.model.entity.PrincipalGroupScope
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.PrincipalGroupRepository
import com.docuhyphen.app.api.repository.UserContactRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.util.UUID

/**
 * Write-side service for the new unified group model (`principal_group` /
 * `principal_group_member`). Replaces the persistence half of [OrganizationGroupService];
 * the legacy service delegates here during the dual-write window so the new tables stay in
 * sync while the legacy `organization_group*` tables remain authoritative for reads until
 * cutover.
 *
 * All methods are id-keyed and idempotent: ORG-scoped groups reuse the legacy group's UUID
 * (V8 backfilled them with matching ids), so an upsert may target a row that already exists.
 *
 * Joins the caller's active transaction (operations go through [PrincipalGroupRepository] /
 * [PrincipalGroupMemberRepository], whose mutating methods are `@Transactional`).
 */
@ApplicationScoped
class PrincipalGroupService @Inject constructor(
    private val groupRepository: PrincipalGroupRepository,
    private val memberRepository: PrincipalGroupMemberRepository,
    private val userContactRepository: UserContactRepository,
)
{
    fun getActiveGroupIdsForPrincipal(principalKind: PrincipalKind, principalId: UUID): Set<UUID> =
        memberRepository.findGroupsForPrincipal(principalKind, principalId)
            .map { it.principalGroupId }
            .toSet()

    /** A desired member of a group, after the caller has mapped legacy permissions to a role. */
    data class GroupMemberSpec(
        val principalId: UUID,
        val principalKind: PrincipalKind = PrincipalKind.USER,
        val groupRole: PrincipalGroupRoleName = PrincipalGroupRoleName.MEMBER,
    )
    {
        /** Backwards-compat convenience for the ORG sync path that only deals with USER principals. */
        val appUserId: UUID get() = principalId
    }

    /**
     * Create or update the ORG-scoped [PrincipalGroup] mirroring a legacy organization group.
     * [groupId] is the legacy group's id (reused so existing FKs keep resolving).
     */
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

    /**
     * Reconcile the active membership of [groupId] to exactly [desiredMembers]:
     * members absent from the desired set are deactivated, present members have their role
     * updated (and are reactivated if previously inactive), and new members are inserted.
     * Only USER-kind membership is handled here (participants/nested groups come later).
     */
    fun syncMembers(
        groupId: UUID,
        desiredMembers: List<GroupMemberSpec>,
        addedByAppUserId: UUID? = null,
    )
    {
        val desiredById = desiredMembers.associateBy { it.appUserId }
        val existing = memberRepository.findActiveMembers(groupId)
            .filter { it.principalKind == PrincipalKind.USER }
        val existingById = existing.associateBy { it.principalId }

        // Deactivate members no longer desired.
        for (member in existing)
        {
            if (member.principalId !in desiredById)
            {
                member.isActive = false
                memberRepository.update(member)
            }
        }

        // Upsert desired members.
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
        }
    }

    /** Soft-delete: deactivate the group (kept when it is still linked to live sessions). */
    fun deactivateGroup(groupId: UUID)
    {
        groupRepository.findById(groupId)?.let {
            it.isActive = false
            groupRepository.update(it)
        }
    }

    /** Hard-delete: remove the group and all its member rows. */
    fun deleteGroup(groupId: UUID)
    {
        memberRepository.findActiveMembers(groupId).forEach { memberRepository.delete(it) }
        groupRepository.findById(groupId)?.let { groupRepository.delete(it) }
    }

    // -------------------------------------------------------------------------
    // PERSONAL-scope group operations
    // -------------------------------------------------------------------------

    /**
     * Creates a new PERSONAL-scope group owned by [ownerAppUserId]. The owner is automatically
     * inserted as a [PrincipalGroupMember] with [PrincipalGroupRoleName.OWNER].
     */
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

        // Insert the owner as a group member with OWNER role.
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

    /** Rename a personal group (name and/or description). */
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

    /**
     * Add members to a personal group. Each member must pass the reciprocity gate:
     * - USER principals must be mutual contacts of [actorId].
     * - PARTICIPANT / EMAIL principals bypass the gate.
     */
    fun addPersonalMembers(groupId: UUID, members: List<GroupMemberSpec>, actorId: UUID)
    {
        val group = groupRepository.findById(groupId)
            ?: throw IllegalArgumentException("Group not found: $groupId")
        require(group.scope == PrincipalGroupScope.PERSONAL) { "Group $groupId is not a PERSONAL group" }

        for (spec in members)
        {
            // Reciprocity gate: USER principals must be mutual contacts.
            if (spec.principalKind == PrincipalKind.USER)
            {
                require(userContactRepository.isMutualContact(actorId, spec.principalId)) {
                    "Cannot add user ${spec.principalId}: not a mutual contact"
                }
            }
            // Reject unsupported principal kinds.
            require(spec.principalKind == PrincipalKind.USER || spec.principalKind == PrincipalKind.PARTICIPANT) {
                "Only USER and PARTICIPANT principals may be added to a personal group"
            }

            // Prevent adding a role higher than MEMBER (only the owner should be OWNER).
            val effectiveRole = if (spec.groupRole == PrincipalGroupRoleName.OWNER)
                PrincipalGroupRoleName.MEMBER else spec.groupRole

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
        }
    }

    /** Remove a single member from a personal group (soft-deactivate). Cannot remove the OWNER. */
    fun removePersonalMember(groupId: UUID, principalKind: PrincipalKind, principalId: UUID)
    {
        val member = memberRepository.findMembership(groupId, principalKind, principalId)
            ?: throw IllegalArgumentException("Member not found in group $groupId")
        require(member.groupRole != PrincipalGroupRoleName.OWNER) { "Cannot remove the group owner" }
        member.isActive = false
        memberRepository.update(member)
    }

    /** Soft-delete a personal group and deactivate all its members. */
    @EnforceAdminAction("PERSONAL_GROUP_DELETE")
    @Transactional
    fun deletePersonalGroup(groupId: UUID)
    {
        val group = groupRepository.findById(groupId)
            ?: throw IllegalArgumentException("Group not found: $groupId")
        require(group.scope == PrincipalGroupScope.PERSONAL) { "Group $groupId is not a PERSONAL group" }
        group.isActive = false
        groupRepository.update(group)
        memberRepository.findActiveMembers(groupId).forEach {
            it.isActive = false
            memberRepository.update(it)
        }
    }
}
