package com.dochyphen.app.api.service.organization

import com.dochyphen.app.api.exception.AppUserNotFoundException
import com.dochyphen.app.api.exception.OrganizationNotFoundException
import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.model.entity.AppUser
import com.dochyphen.app.api.model.entity.AppUserRole
import com.dochyphen.app.api.model.entity.Person
import com.dochyphen.app.api.repository.OrganizationRepository
import com.dochyphen.app.api.service.AppUserService
import com.dochyphen.app.api.service.auth.AuthenticationService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.util.*

@RequestScoped
class OrganizationAppUserService @Inject constructor(
    private val organizationGroupService: OrganizationGroupService,
    private val authenticationService: AuthenticationService,
    private val appUserService: AppUserService,
    private val authTokenContext: AuthTokenContext,
    private val organizationRepository: OrganizationRepository,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationAppUserService::class.java)
    }

    @Transactional
    fun addAppUser(
        organizationId: String,
        role: AppUserRole?,
        email: String?,
        firstName: String?,
        lastName: String?
    ): AppUser
    {
        if (authTokenContext.authToken.appUser?.role != AppUserRole.ORG_ADMIN)
        {
            throw IllegalArgumentException("User does not have permission to create groups")
        }

        val organization = organizationGroupService.getOrganizationById(UUID.fromString(organizationId))
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")

        if (role == null)
        {
            throw IllegalArgumentException("Role cannot be null")
        }

        if (email.isNullOrBlank() || authenticationService.isEmailInvalid(email))
        {
            throw IllegalArgumentException("A valid email is required")
        }

        if (firstName.isNullOrBlank())
        {
            throw IllegalArgumentException("First name cannot be blank")
        }

        if (lastName.isNullOrBlank())
        {
            throw IllegalArgumentException("Last name cannot be blank")
        }

        organization.appUsers.find { it -> it.email.trim().lowercase() == email.trim().lowercase() } ?:
        {
            throw IllegalArgumentException("Email already exists")
        }

        if (organization.appUsers.any { it.email.lowercase() == email.lowercase() })
        {
            throw IllegalArgumentException("App user with that email already exists")
        }

        var appUserPerson = Person().apply {
            this.firstName = firstName
            this.lastName = lastName
        }

        val appUser = AppUser().apply {
            this.email = email
            this.role = role
            person = appUserPerson
        }

        organization.appUsers.add(appUser)

        organizationRepository.update(organization)

        return appUser
    }

    fun getAppUsers(organizationId: String?): List<AppUser>
    {
        if (organizationId.isNullOrBlank())
        {
            throw IllegalArgumentException("Organization ID cannot be null")
        }

        val organization = organizationGroupService.getOrganizationById(UUID.fromString(organizationId))
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")

        return organization.appUsers
    }

    @Transactional
    fun updateAppUser(
        organizationId: String?,
        appUserId: String?,
        role: String?,
        isActive: Boolean?,
        email: String?,
        firstName: String?,
        lastName: String?
    )
    {
        if (authTokenContext.authToken.appUser?.role != AppUserRole.ORG_ADMIN)
        {
            throw IllegalArgumentException("User does not have permission to update app users")
        }

        if (organizationId.isNullOrBlank())
        {
            throw IllegalArgumentException("Organization ID cannot be null or blank")
        }

        if (appUserId.isNullOrBlank())
        {
            throw IllegalArgumentException("App User ID cannot be null or blank")
        }

        organizationGroupService.getOrganizationById(UUID.fromString(organizationId.toString()))
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")

        val appUser = appUserService.getById(UUID.fromString(appUserId))
            ?: throw AppUserNotFoundException("App user not found for id: $appUserId")

        isActive?.let {

            appUser.isActive = isActive
        } ?: run {
            throw IllegalArgumentException("isActive cannot be null")
        }

        role?.let {

            try
            {
                AppUserRole.valueOf(role)
            }
            catch (e: IllegalArgumentException)
            {
                throw IllegalArgumentException("Invalid role: $role")
            }
        }

        email?.let {

            if (it.isBlank() || authenticationService.isEmailInvalid(it))
            {
                throw IllegalArgumentException("A valid email is required")
            }

            val appUserByEmail = appUserService.getAppUserByEmail(it)

            if (appUserByEmail != null && appUser.id != appUserByEmail.id)
            {
                throw IllegalArgumentException("Email already exists")
            }

            appUser.email = it.trim()

            //ToDo: send email reset link
        }

        appUser.person?.let {

            if (firstName.isNullOrBlank())
            {
                throw IllegalArgumentException("First name cannot be blank")
            }

            if (lastName.isNullOrBlank())
            {
                throw IllegalArgumentException("Last name cannot be blank")
            }

            it.firstName = firstName.trim()
            it.lastName = lastName.trim()

        } ?: run {
            throw IllegalArgumentException("Person cannot be null")
        }

        appUserService.update(appUser)
    }

    fun deactivateAppUser(organizationId: String?, appUserId: String?)
    {
        if (authTokenContext.authToken.appUser?.role != AppUserRole.ORG_ADMIN)
        {
            throw IllegalArgumentException("User does not have permission to create groups")
        }

        if (organizationId.isNullOrBlank())
        {
            throw IllegalArgumentException("Organization ID cannot be null or blank")
        }

        if (appUserId.isNullOrBlank())
        {
            throw IllegalArgumentException("App User ID cannot be null or blank")
        }

        organizationGroupService.getOrganizationById(UUID.fromString(organizationId.toString()))
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")

        val appUser = appUserService.getById(UUID.fromString(appUserId.toString()))
            ?: throw AppUserNotFoundException("App user not found for id: $appUserId")

        appUser.isActive = false

        appUserService.update(appUser)
    }
}