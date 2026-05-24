package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.extension.normalizeEmailOrNull
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AppUserRole
import com.docuhyphen.app.api.model.entity.Person
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.AdminActionGuardService
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthenticationService
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
    private val adminActionGuardService: AdminActionGuardService,
    private val authAuditService: AuthAuditService,
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
        lastName: String?,
        adminApprovalContext: AdminApprovalContext,
    ): AppUser
    {
        if (authTokenContext.authToken.appUser?.role != AppUserRole.ORG_ADMIN)
        {
            throw IllegalArgumentException("User does not have permission to create groups")
        }

        adminActionGuardService.enforce(
            action = "ORG_APP_USER_ADD",
            actorId = authTokenContext.authToken.appUser?.id,
            context = adminApprovalContext,
            requireDualApproval = false,
        )

        val organization = organizationGroupService.getOrganizationById(UUID.fromString(organizationId))
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")

        if (role == null)
        {
            throw IllegalArgumentException("Role cannot be null")
        }

        val normalizedEmail = email.normalizeEmailOrNull()

        if (normalizedEmail == null || authenticationService.isEmailInvalid(normalizedEmail))
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

        if (organization.appUsers.any { it.email.normalizeEmailOrNull() == normalizedEmail })
        {
            throw IllegalArgumentException("App user with that email already exists")
        }

        val appUserPerson = Person().apply {
            this.firstName = firstName
            this.lastName = lastName
        }

        val appUser = AppUser().apply {
            this.email = normalizedEmail
            this.role = role
            person = appUserPerson
        }

        organization.appUsers.add(appUser)

        organizationRepository.update(organization)

        authAuditService.emit(
            action = "ORG_APP_USER_ADD",
            outcome = "SUCCESS",
            actorId = authTokenContext.authToken.appUser?.id,
            requestId = adminApprovalContext.requestId,
            reason = "Organization admin added app user",
            beforeSnapshot = null,
            afterSnapshot = appUserSnapshot(appUser),
        )

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
        lastName: String?,
        adminApprovalContext: AdminApprovalContext,
    )
    {
        if (authTokenContext.authToken.appUser?.role != AppUserRole.ORG_ADMIN)
        {
            throw IllegalArgumentException("User does not have permission to update app users")
        }

        adminActionGuardService.enforce(
            action = "ORG_APP_USER_UPDATE",
            actorId = authTokenContext.authToken.appUser?.id,
            context = adminApprovalContext,
            requireDualApproval = isActive == false,
        )

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

        val beforeSnapshot = appUserSnapshot(appUser)

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
            val normalizedEmail = it.normalizeEmailOrNull()

            if (normalizedEmail == null || authenticationService.isEmailInvalid(normalizedEmail))
            {
                throw IllegalArgumentException("A valid email is required")
            }

            val appUserByEmail = appUserService.getAppUserByEmail(normalizedEmail)

            if (appUserByEmail != null && appUser.id != appUserByEmail.id)
            {
                throw IllegalArgumentException("Email already exists")
            }

            appUser.email = normalizedEmail

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

        authAuditService.emit(
            action = "ORG_APP_USER_UPDATE",
            outcome = "SUCCESS",
            actorId = authTokenContext.authToken.appUser?.id,
            requestId = adminApprovalContext.requestId,
            reason = "Organization admin updated app user",
            beforeSnapshot = beforeSnapshot,
            afterSnapshot = appUserSnapshot(appUser),
        )
    }

    @Transactional
    fun deleteAppUser(organizationId: String?, appUserId: String?, adminApprovalContext: AdminApprovalContext)
    {
        if (authTokenContext.authToken.appUser?.role != AppUserRole.ORG_ADMIN)
        {
            throw IllegalArgumentException("User does not have permission to create groups")
        }

        adminActionGuardService.enforce(
            action = "ORG_APP_USER_DELETE",
            actorId = authTokenContext.authToken.appUser?.id,
            context = adminApprovalContext,
            requireDualApproval = true,
        )

        if (organizationId.isNullOrBlank())
        {
            throw IllegalArgumentException("Organization ID cannot be null or blank")
        }

        if (appUserId.isNullOrBlank())
        {
            throw IllegalArgumentException("App User ID cannot be null or blank")
        }

        val organization = organizationGroupService.getOrganizationById(UUID.fromString(organizationId.toString()))
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")

        val appUser = appUserService.getById(UUID.fromString(appUserId.toString()))
            ?: throw AppUserNotFoundException("App user not found for id: $appUserId")

        val beforeSnapshot = appUserSnapshot(appUser)

        if (!isAppUserIsDeletable(organizationId, appUserId))
        {
            throw IllegalArgumentException("App user cannot be deleted")
        }

        val appUserUuid = UUID.fromString(appUserId)

        // First remove the app user from all organization groups
        for (group in organization.groups) {
            val membersToRemove = group.members.filter { it.appUser?.id == appUserUuid }
            if (membersToRemove.isNotEmpty()) {
                group.members.removeAll(membersToRemove)
            }
        }

        // Then remove the app user from the organization
        organization.appUsers.remove(appUser)

        // Update the entire organization which will cascade to groups
        organizationRepository.update(organization)

        // Now it's safe to delete the app user
        appUserService.delete(appUser.id.toString())

        authAuditService.emit(
            action = "ORG_APP_USER_DELETE",
            outcome = "SUCCESS",
            actorId = authTokenContext.authToken.appUser?.id,
            requestId = adminApprovalContext.requestId,
            reason = "Organization admin deleted app user",
            beforeSnapshot = beforeSnapshot,
            afterSnapshot = "deleted",
        )
    }

    fun isAppUserIsDeletable(organizationId: String?, appUserId: String?): Boolean
    {
        if (organizationId.isNullOrBlank())
        {
            throw IllegalArgumentException("Organization ID cannot be null or blank")
        }

        if (appUserId.isNullOrBlank())
        {
            throw IllegalArgumentException("App User ID cannot be null or blank")
        }

        val organization = organizationGroupService.getOrganizationById(UUID.fromString(organizationId))
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")

        val appUser = appUserService.getById(UUID.fromString(appUserId))
            ?: throw AppUserNotFoundException("App user not found for id: $appUserId")

        return organization.appUsers.contains(appUser)
//                && appUser.role != AppUserRole.ORG_ADMIN
                && appUserService.hasLinkedSharingSessions(appUser.id) == false
    }

    private fun appUserSnapshot(appUser: AppUser): String
    {
        return "id=${appUser.id};email=${appUser.email};role=${appUser.role};isActive=${appUser.isActive};firstName=${appUser.person?.firstName};lastName=${appUser.person?.lastName}"
    }
}