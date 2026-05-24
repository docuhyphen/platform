package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.OrganizationGroupNotFoundException
import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.resourceservice.OrganizationGroupMemberModel
import com.docuhyphen.app.api.repository.OrganizationGroupRepository
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.repository.SharingSessionParticipantRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.AdminActionGuardService
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.util.*

@RequestScoped
class OrganizationGroupService @Inject constructor(
    private val organizationRepository: OrganizationRepository,
    private val orgGroupRepo: OrganizationGroupRepository,
    private val sharingSessionParticipantRepository: SharingSessionParticipantRepository,
    private val authTokenContext: AuthTokenContext,
    private val appUserService: AppUserService,
    private val adminActionGuardService: AdminActionGuardService,
    private val authAuditService: AuthAuditService,
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService,
    private val configurationService: ConfigurationService,
)
{
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationGroupService::class.java)
    }

    fun getById(groupId: String?): OrganizationGroup?
    {
        return groupId ?.let {
            orgGroupRepo.findById(UUID.fromString(groupId))
        } ?: throw java.lang.IllegalArgumentException("Group id required")
    }

    fun getOrganizationByAppUserIdAndPersonId(appUserId: UUID, personId: UUID): Organization
    {
        return organizationRepository.findByAppUserIdAndPersonId(appUserId, personId)
            ?: throw OrganizationNotFoundException("Organization not found for appUserId: $appUserId and personId: $personId")
    }

    //For app integrations
    fun getOrganizationByAppUserIdAndAppId(appUserId: UUID, appId: UUID): Organization
    {
        return organizationRepository.findByAppUserIdAndAppId(appUserId, appId)
            ?: throw OrganizationNotFoundException("Organization not found for appUserId: $appUserId and appId: $appId")
    }

    @Transactional
    fun addOrganizationGroup(
        organizationId: String,
        name: String?,
        members: List<OrganizationGroupMemberModel>,
        adminApprovalContext: AdminApprovalContext,
    )
    {
        if (authTokenContext.authToken.appUser?.role != AppUserRole.ORG_ADMIN)
        {
            throw IllegalArgumentException("User does not have permission to create groups")
        }

        adminActionGuardService.enforce(
            action = "ORG_GROUP_ADD",
            actorId = authTokenContext.authToken.appUser?.id,
            context = adminApprovalContext,
            requireDualApproval = false,
        )

        val organization = organizationRepository.findById(UUID.fromString(organizationId))
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")

        if (name.isNullOrBlank())
        {
            throw IllegalArgumentException("Group name cannot be blank")
        }

        if (members.isEmpty())
        {
            throw IllegalArgumentException("Group members cannot be empty")
        }

        if (organization.groups.any { it.name.lowercase() == name.lowercase() })
        {
            throw IllegalArgumentException("Group name already exists")
        }

        val newGroup = OrganizationGroup().apply {
            this.name = name.trim()
        }

        members.forEach { memberModel ->
            val appUser = appUserService.getById(UUID.fromString(memberModel.appUserId))
                ?: throw IllegalArgumentException("Member not found for id: ${memberModel.appUserId}")

            organization.appUsers.find { it.id.toString() == memberModel.appUserId }
                ?: throw IllegalArgumentException("Member does not belong to the organization")

            val groupMember = OrganizationGroupMember().apply {
                this.appUser = appUser
                this.organizationGroup = newGroup
            }

            val memberPermission = OrganizationGroupMemberPermission().apply {
                this.allowSessionAccept = memberModel.permissions.allowSessionAccept
                this.allowSessionReject = memberModel.permissions.allowSessionReject
                this.allowSessionEdit = memberModel.permissions.allowSessionEdit
                this.allowSessionDelete = memberModel.permissions.allowSessionDelete
                this.allowSessionEnd = memberModel.permissions.allowSessionEnd
                this.allowDocumentAddition = memberModel.permissions.allowDocumentAddition
                this.allowDocumentDeletion = memberModel.permissions.allowDocumentDeletion
                this.allowDocumentDownload = memberModel.permissions.allowDocumentDownload
                this.allowDocumentUpdate = memberModel.permissions.allowDocumentUpdate
                this.allowDocumentUpload = memberModel.permissions.allowDocumentUpload
                this.organizationGroupMember = groupMember
            }

            groupMember.permissions = memberPermission
            newGroup.members.add(groupMember)
        }

        organization.groups.add(newGroup)
        //using the repo throws an error: Transaction is not active
        entityManager.merge(organization)
        entityManager.flush()

        authAuditService.emit(
            action = "ORG_GROUP_ADD",
            outcome = "SUCCESS",
            actorId = authTokenContext.authToken.appUser?.id,
            organizationId = organization.id,
            requestId = adminApprovalContext.requestId,
            reason = "Organization admin added group",
            beforeSnapshot = null,
            afterSnapshot = groupSnapshot(newGroup),
        )

        newGroup.members.forEach { member ->
            member.appUser?.let { sendGroupMemberAddedEmail(it, newGroup.name, organization.name) }
        }
    }

    fun getOrganizationGroups(organizationId: String): List<OrganizationGroup>
    {
        val organization = organizationRepository.findById(UUID.fromString(organizationId))
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")

        return organization.groups
    }

    fun getOrganizationById(uUID: UUID): Organization?
    {
        return organizationRepository.findById(uUID)
            ?: throw OrganizationNotFoundException("Organization not found for id: $uUID")
    }

    @Transactional
    fun deleteOrganizationGroup(organizationId: String?, groupId: String?, adminApprovalContext: AdminApprovalContext)
    {
        if (authTokenContext.authToken.appUser?.role != AppUserRole.ORG_ADMIN)
        {
            throw IllegalArgumentException("User does not have permission to create groups")
        }

        adminActionGuardService.enforce(
            action = "ORG_GROUP_DELETE",
            actorId = authTokenContext.authToken.appUser?.id,
            context = adminApprovalContext,
            requireDualApproval = true,
        )

        if (organizationId.isNullOrBlank() || groupId.isNullOrBlank())
        {
            throw OrganizationNotFoundException("Organization not found for id: $organizationId")
        }

        val organization = organizationRepository.findById(UUID.fromString(organizationId))
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")

        val group = organization.groups.find { it.id.toString() == groupId }
            ?: throw OrganizationGroupNotFoundException("Group not found for id: $groupId")
        val beforeSnapshot = groupSnapshot(group)

        val linkedSessionCount = sharingSessionParticipantRepository.countByOrganizationGroupId(group.id)

        if (linkedSessionCount > 0)
        {
            logger.info("Group ${group.name} is linked to $linkedSessionCount sessions. Deactivating instead of deleting.")
            group.isActive = false
            organizationRepository.update(organization)

            authAuditService.emit(
                action = "ORG_GROUP_DELETE",
                outcome = "SUCCESS",
                actorId = authTokenContext.authToken.appUser?.id,
                organizationId = organization.id,
                requestId = adminApprovalContext.requestId,
                reason = "Group linked to active sessions, deactivated instead of hard delete",
                beforeSnapshot = beforeSnapshot,
                afterSnapshot = groupSnapshot(group),
            )
        }
        else
        {
            organization.groups.remove(group)
            organizationRepository.update(organization)

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
    fun updateOrganizationGroup(
        organizationId: String?,
        groupId: String?,
        groupName: String?,
        isActive: Boolean,
        members: List<OrganizationGroupMemberModel>,
        adminApprovalContext: AdminApprovalContext,
    )
    {
        if (authTokenContext.authToken.appUser?.role != AppUserRole.ORG_ADMIN)
        {
            throw IllegalArgumentException("User does not have permission to create groups")
        }

        adminActionGuardService.enforce(
            action = "ORG_GROUP_UPDATE",
            actorId = authTokenContext.authToken.appUser?.id,
            context = adminApprovalContext,
            requireDualApproval = !isActive,
        )

        if (organizationId.isNullOrBlank() || groupId.isNullOrBlank())
        {
            throw OrganizationNotFoundException("Organization not found for id: $organizationId")
        }

        if (groupName.isNullOrBlank())
        {
            throw IllegalArgumentException("Group name cannot be blank")
        }

        val organization = organizationRepository.findById(UUID.fromString(organizationId))
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")

        val group = organization.groups.find { it.id.toString() == groupId }
            ?: throw OrganizationGroupNotFoundException("Group not found for id: $groupId")
        val beforeSnapshot = groupSnapshot(group)

        val groupNameExists = organization.groups
            .filter { it.id != group.id }
            .any { it -> it.name.trim().lowercase() == groupName.lowercase().trim() }

        if (groupNameExists)
        {
            throw IllegalArgumentException("Group name already exists in this organization")
        }

        val previousName = group.name
        val previousActive = group.isActive
        val previousMemberIds = group.members.mapNotNull { it.appUser?.id }.toSet()
        val newMemberIds = members.map { UUID.fromString(it.appUserId) }.toSet()
        val groupUpdateFields = mutableListOf<String>()
        if (previousName != groupName.trim())
        {
            groupUpdateFields.add("Renamed from \"$previousName\" to \"${groupName.trim()}\"")
        }
        if (previousActive != isActive)
        {
            groupUpdateFields.add(if (isActive) "Group reactivated" else "Group deactivated")
        }

        group.name = groupName.trim()
        group.isActive = isActive
        group.members.clear()

        members.forEach { memberModel ->
            val appUser = appUserService.getById(UUID.fromString(memberModel.appUserId))
                ?: throw IllegalArgumentException("Member not found for id: ${memberModel.appUserId}")

            organization.appUsers.find { it.id.toString() == memberModel.appUserId }
                ?: throw IllegalArgumentException("Member does not belong to the organization")

            val groupMember = OrganizationGroupMember().apply {
                this.appUser = appUser
                this.organizationGroup = group
            }

            val memberPermission = OrganizationGroupMemberPermission().apply {
                this.allowSessionAccept = memberModel.permissions.allowSessionAccept
                this.allowSessionReject = memberModel.permissions.allowSessionReject
                this.allowSessionEdit = memberModel.permissions.allowSessionEdit
                this.allowSessionDelete = memberModel.permissions.allowSessionDelete
                this.allowSessionEnd = memberModel.permissions.allowSessionEnd
                this.allowDocumentAddition = memberModel.permissions.allowDocumentAddition
                this.allowDocumentDeletion = memberModel.permissions.allowDocumentDeletion
                this.allowDocumentDownload = memberModel.permissions.allowDocumentDownload
                this.allowDocumentUpdate = memberModel.permissions.allowDocumentUpdate
                this.allowDocumentUpload = memberModel.permissions.allowDocumentUpload
                this.organizationGroupMember = groupMember
            }

            groupMember.permissions = memberPermission
            group.members.add(groupMember)
        }

        organizationRepository.update(organization)

        authAuditService.emit(
            action = "ORG_GROUP_UPDATE",
            outcome = "SUCCESS",
            actorId = authTokenContext.authToken.appUser?.id,
            organizationId = organization.id,
            requestId = adminApprovalContext.requestId,
            reason = "Organization admin updated group",
            beforeSnapshot = beforeSnapshot,
            afterSnapshot = groupSnapshot(group),
        )

        val addedMemberIds = newMemberIds - previousMemberIds
        val removedMemberIds = previousMemberIds - newMemberIds
        val retainedMemberIds = previousMemberIds intersect newMemberIds

        addedMemberIds.forEach { memberId ->
            appUserService.getById(memberId)?.let { sendGroupMemberAddedEmail(it, group.name, organization.name) }
        }
        removedMemberIds.forEach { memberId ->
            appUserService.getById(memberId)?.let { sendGroupMemberRemovedEmail(it, group.name, organization.name) }
        }
        if (groupUpdateFields.isNotEmpty())
        {
            retainedMemberIds.forEach { memberId ->
                appUserService.getById(memberId)?.let {
                    sendGroupUpdatedEmail(it, group.name, organization.name, groupUpdateFields)
                }
            }
        }
    }

    @Transactional
    fun updateOrganizationGroup(group: OrganizationGroup)
    {

    }

    private fun actorLabel(): String
    {
        val actor = authTokenContext.authToken.appUser ?: return "an administrator"
        return actor.person?.let { "${it.firstName} ${it.lastName}" } ?: actor.email
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

    private fun groupSnapshot(group: OrganizationGroup): String
    {
        val members = group.members
            .map { member ->
                val userId = member.appUser?.id?.toString().orEmpty()
                val permissions = member.permissions
                "member=$userId:accept=${permissions?.allowSessionAccept};reject=${permissions?.allowSessionReject};edit=${permissions?.allowSessionEdit};delete=${permissions?.allowSessionDelete};end=${permissions?.allowSessionEnd};docAdd=${permissions?.allowDocumentAddition};docDelete=${permissions?.allowDocumentDeletion};docDownload=${permissions?.allowDocumentDownload};docUpdate=${permissions?.allowDocumentUpdate};docUpload=${permissions?.allowDocumentUpload}"
            }
            .sorted()
            .joinToString("|")

        return "id=${group.id};name=${group.name};isActive=${group.isActive};members=[${members}]"
    }

}