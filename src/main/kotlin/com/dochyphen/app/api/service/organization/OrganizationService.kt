package com.dochyphen.app.api.service.organization

import com.dochyphen.app.api.exception.OrganizationNotFoundException
import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.model.entity.AppUser
import com.dochyphen.app.api.model.entity.AppUserRole.ORG_ADMIN
import com.dochyphen.app.api.model.entity.Organization
import com.dochyphen.app.api.model.entity.OrganizationGroup
import com.dochyphen.app.api.repository.OrganizationRepository
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.util.*

@RequestScoped
class OrganizationService @Inject constructor(
    private val organizationGroupService: OrganizationGroupService,
    private val authTokenContext: AuthTokenContext,
    private val organizationRepository: OrganizationRepository,
)
{
    @Transactional
    fun updateOrganization(
        organizationId: String?,
        name: String?,
        registrationNumber: String?,
    )
    {
        if (authTokenContext.authToken.appUser?.role != ORG_ADMIN)
        {
            throw UnauthorizedException("User does not have permission to update organizations")
        }

        if (organizationId.isNullOrBlank())
        {
            throw OrganizationNotFoundException("Organization ID cannot be null or blank")
        }

        val organization = organizationGroupService.getOrganizationById(UUID.fromString(organizationId))
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")

        if (!name.isNullOrBlank())
        {
            organization.name = name
        }

        if (!registrationNumber.isNullOrBlank())
        {
            organization.registrationNumber = registrationNumber
        }

        organizationRepository.update(organization)
    }

    fun getOrganizationById(organizationId: UUID): Organization
    {
        return organizationRepository.findById(organizationId)
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")
    }

    fun getAppUsers(organizationId: String?): List<AppUser>
    {
        if (organizationId.isNullOrBlank())
        {
            throw OrganizationNotFoundException("Organization ID cannot be null or blank")
        }

        val organization = organizationGroupService.getOrganizationById(UUID.fromString(organizationId))
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")

        return organization.appUsers
    }

    fun update(organization: Organization)
    {
        organizationRepository.update(organization)
    }

    fun getLinkedOrganizations(includePublic: Boolean): List<Organization>
    {
        val currentAppUser = authTokenContext.authToken.appUser
        val currentAppUserPerson = authTokenContext.authToken.appUser?.person!!

        val currentAppUserOrg = organizationRepository.findByAppUserIdAndPersonId(
            currentAppUser?.id!!,
            currentAppUserPerson.id
        )

        return organizationRepository.getLinkedOrganizations(currentAppUserOrg?.id!!, includePublic)
    }

    fun getLinkedOrganizationsAppUsers(organizationId: String?): List<AppUser>
    {
        val currentAppUser = authTokenContext.authToken.appUser

        return getAppUsers(organizationId)
    }

    fun getLinkedOrganizationsGroups(organizationId: String?): List<OrganizationGroup>
    {
        if (organizationId.isNullOrBlank())
        {
            throw OrganizationNotFoundException("organization is required")
        }

        val currentAppUser = authTokenContext.authToken.appUser

        return organizationGroupService.getOrganizationGroups(organizationId)
    }
}

