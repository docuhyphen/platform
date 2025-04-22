package com.dochyphen.app.api.service

import com.dochyphen.app.api.exception.OrganizationNotFoundException
import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.model.entity.AppUserRole
import com.dochyphen.app.api.model.entity.Organization
import com.dochyphen.app.api.model.entity.OrganizationGroup
import com.dochyphen.app.api.repository.OrganizationRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.util.*

@RequestScoped
class OrganizationService @Inject constructor(
    private val organizationRepository: OrganizationRepository,
    private val authTokenContext: AuthTokenContext,
    private val appUserService: AppUserService
)
{
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

    fun addOrganizationGroup(
        organizationId: String,
        name: String?,
        members: List<String>?
    )
    {
        val organization = organizationRepository.findById(UUID.fromString(organizationId))
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")

        if (name.isNullOrBlank())
        {
            throw IllegalArgumentException("Group name cannot be blank")
        }

        if (members.isNullOrEmpty())
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

        // Check if the user is part of the organization
        val members = members.map { memberId ->

            val appUser = appUserService.getAppUserById(UUID.fromString(memberId))
                ?: throw IllegalArgumentException("Member not found for id: $memberId")

            var appUserOrgId = organization.appUsers.find { it.id.toString() == memberId }?.id

            if (appUserOrgId != null && appUserOrgId != organization.id)
            {
                throw IllegalArgumentException("Member does not belong to the organization")
            }

            appUser
        }

        val newGroup = OrganizationGroup().apply {
            this.name = name
            this.members = members.toMutableList()
        }

        organization.groups.add(newGroup)
        organizationRepository.update(organization)
    }

    fun getOrganizationGroups(organizationId: String): Response
    {
        val organization = organizationRepository.findById(UUID.fromString(organizationId))
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")

        return Response.ok(organization.groups).build()
    }
}