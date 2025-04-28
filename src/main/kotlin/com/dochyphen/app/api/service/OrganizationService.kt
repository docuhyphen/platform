package com.dochyphen.app.api.service

import com.dochyphen.app.api.exception.OrganizationNotFoundException
import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.model.entity.*
import com.dochyphen.app.api.model.resourceservice.OrganizationGroupMemberModel
import com.dochyphen.app.api.repository.OrganizationRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.util.*

@RequestScoped
class OrganizationService @Inject constructor(
    private val organizationRepository: OrganizationRepository,
    private val authTokenContext: AuthTokenContext,
    private val appUserService: AppUserService
)
{
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationService::class.java)
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
        members: List<OrganizationGroupMemberModel>
    )
    {
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

        if (authTokenContext.authToken.appUser?.role != AppUserRole.ORG_ADMIN)
        {
            throw IllegalArgumentException("User does not have permission to create groups")
        }

        val newGroup = OrganizationGroup().apply {
            this.name = name
        }

        members.forEach { memberModel ->
            val appUser = appUserService.getAppUserById(UUID.fromString(memberModel.appUserId))
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
    }

    fun getOrganizationGroups(organizationId: String): List<OrganizationGroup>
    {
        val organization = organizationRepository.findById(UUID.fromString(organizationId))
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")

        return organization.groups
    }
}