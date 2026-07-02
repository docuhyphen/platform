package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.extension.normalizeEmailOrNull
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.Person
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.repository.OrganizationSubscriptionPolicyRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.AdminActionGuardService
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.PlatformOrganizationSubscriptionPolicyService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

@RequestScoped
class OrganizationAppUserService @Inject constructor(
    private val organizationGroupService: OrganizationGroupService,
    private val authenticationService: AuthenticationService,
    private val appUserService: AppUserService,
    private val authTokenContext: AuthTokenContext,
    private val organizationSubscriptionPolicyRepository: OrganizationSubscriptionPolicyRepository,
    private val adminActionGuardService: AdminActionGuardService,
    private val authAuditService: AuthAuditService,
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService,
    private val configurationService: ConfigurationService,
    private val userRoleService: com.docuhyphen.app.api.service.auth.UserRoleService,
    private val organizationMembershipService: OrganizationMembershipService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationAppUserService::class.java)
        private const val TEMP_PASSWORD_LENGTH = 12
        private const val TEMP_PASSWORD_EXPIRY_DAYS = 7L
        private val TEMP_PASSWORD_EXPIRY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC)
    }

    @Transactional
    fun addAppUser(
        organizationId: String,
        roles: Set<OrganizationRoleName>,
        email: String?,
        firstName: String?,
        lastName: String?,
        adminApprovalContext: AdminApprovalContext,
    ): AppUser
    {
        val targetOrgId = try { UUID.fromString(organizationId) }
            catch (e: IllegalArgumentException) { throw IllegalArgumentException("Invalid organization ID") }
        if (authTokenContext.authToken.appUser?.id?.let { userRoleService.isOrgAdminIn(it, targetOrgId) } != true)
        {
            throw IllegalArgumentException("User does not have permission to add members to this organization")
        }

        adminActionGuardService.enforce(
            action = "ORG_APP_USER_ADD",
            actorId = authTokenContext.authToken.appUser?.id,
            context = adminApprovalContext,
        )

        val organization = organizationGroupService.getOrganizationById(UUID.fromString(organizationId))
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")

        if (roles.isEmpty())
        {
            throw IllegalArgumentException("At least one organization role is required")
        }
        val assignedRoles = withParticipationRole(roles)

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

        if (organizationMembershipService.membersOf(organization.id)
                .any { it.email.normalizeEmailOrNull() == normalizedEmail })
        {
            throw IllegalArgumentException("App user with that email already exists")
        }

        enforceOrganizationUserCap(organization)

        val appUserPerson = Person().apply {
            this.firstName = firstName
            this.lastName = lastName
        }

        val appUser = AppUser().apply {
            this.email = normalizedEmail
            person = appUserPerson
        }

        val temporaryPassword = generateTemporaryPassword()
        val temporaryPasswordExpiry = Timestamp.from(Instant.now().plusSeconds(TEMP_PASSWORD_EXPIRY_DAYS * 24 * 60 * 60))
        applyTemporaryPassword(appUser, temporaryPassword, temporaryPasswordExpiry)

        // Persist the new user directly (the org→users join column is retired; org binding is
        // recorded by the membership row below).
        appUserService.create(appUser)

        assignedRoles.forEach { role ->
            organizationMembershipService.assignOrgRole(
                appUserId = appUser.id,
                organizationId = organization.id,
                role = role,
                invitedByAppUserId = authTokenContext.authToken.appUser?.id,
            )
        }

        authAuditService.emit(
            action = "ORG_APP_USER_ADD",
            outcome = "SUCCESS",
            actorId = authTokenContext.authToken.appUser?.id,
            requestId = adminApprovalContext.requestId,
            reason = "Organization admin added app user",
            beforeSnapshot = null,
            afterSnapshot = appUserSnapshot(appUser),
        )

        sendOrganizationMemberAddedEmail(
            appUser = appUser,
            organizationName = organization.name,
            roles = assignedRoles,
            isNewUser = true,
            temporaryPassword = temporaryPassword,
            temporaryPasswordExpiry = temporaryPasswordExpiry,
        )

        return appUser
    }

    fun getAppUsers(organizationId: String?): List<AppUser>
    {
        if (organizationId.isNullOrBlank())
        {
            throw IllegalArgumentException("Organization ID cannot be null")
        }
        val targetOrgId = try { UUID.fromString(organizationId) }
            catch (e: IllegalArgumentException) { throw IllegalArgumentException("Invalid organization ID") }
        if (authTokenContext.authToken.appUser?.id?.let { userRoleService.isOrgAdminIn(it, targetOrgId) } != true)
        {
            throw IllegalArgumentException("User does not have permission to list members of this organization")
        }

        val organization = organizationGroupService.getOrganizationById(targetOrgId)
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")

        return organizationMembershipService.membersOf(organization.id)
    }

    @Transactional
    fun updateAppUser(
        organizationId: String?,
        appUserId: String?,
        rolesToAdd: Set<OrganizationRoleName>,
        rolesToRemove: Set<OrganizationRoleName>,
        isActive: Boolean?,
        email: String?,
        firstName: String?,
        lastName: String?,
        adminApprovalContext: AdminApprovalContext,
    )
    {
        if (organizationId.isNullOrBlank())
        {
            throw IllegalArgumentException("Organization ID cannot be null or blank")
        }
        val targetOrgId = try { UUID.fromString(organizationId) }
            catch (e: IllegalArgumentException) { throw IllegalArgumentException("Invalid organization ID") }
        if (authTokenContext.authToken.appUser?.id?.let { userRoleService.isOrgAdminIn(it, targetOrgId) } != true)
        {
            throw IllegalArgumentException("User does not have permission to update members of this organization")
        }

        adminActionGuardService.enforce(
            action = "ORG_APP_USER_UPDATE",
            actorId = authTokenContext.authToken.appUser?.id,
            context = adminApprovalContext,
        )

        if (appUserId.isNullOrBlank())
        {
            throw IllegalArgumentException("App User ID cannot be null or blank")
        }

        val organization = organizationGroupService.getOrganizationById(UUID.fromString(organizationId.toString()))
            ?: throw OrganizationNotFoundException("Organization not found for id: $organizationId")

        val appUser = appUserService.getById(UUID.fromString(appUserId))
            ?: throw AppUserNotFoundException("App user not found for id: $appUserId")

        val beforeSnapshot = appUserSnapshot(appUser)
        require(rolesToAdd.intersect(rolesToRemove).isEmpty()) {
            "The same organization role cannot be added and removed in one request"
        }
        val previousRoles = userRoleService.orgRolesIn(appUser.id, organization.id)
        val addedRoles = withParticipationRole(rolesToAdd)
        val resultingRoles = (previousRoles + addedRoles) - rolesToRemove
        require(resultingRoles.isNotEmpty()) { "An active organization membership must retain at least one role" }
        val wasActive = appUser.isActive

        isActive?.let {

            appUser.isActive = isActive
        } ?: run {
            throw IllegalArgumentException("isActive cannot be null")
        }

        // Min-admins invariant: an org must always retain at least one usable administrator.
        // Block this update if the target is the org's last enabled admin and the change would
        // demote them (to a non-admin role) or deactivate their account.
        val previousIsAdmin = previousRoles.hasAdminRole()
        if (previousIsAdmin && organizationMembershipService.isLastActiveAdmin(appUser.id, organization.id))
        {
            val demoting = !resultingRoles.hasAdminRole()
            val deactivating = isActive == false
            if (demoting || deactivating)
            {
                throw IllegalArgumentException("Cannot demote or deactivate the last administrator of the organization")
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

        addedRoles.forEach { organizationMembershipService.assignOrgRole(appUser.id, organization.id, it) }
        rolesToRemove.forEach { organizationMembershipService.removeOrgRole(appUser.id, organization.id, it) }

        authAuditService.emit(
            action = "ORG_APP_USER_UPDATE",
            outcome = "SUCCESS",
            actorId = authTokenContext.authToken.appUser?.id,
            requestId = adminApprovalContext.requestId,
            reason = "Organization admin updated app user",
            beforeSnapshot = beforeSnapshot,
            afterSnapshot = appUserSnapshot(appUser),
        )

        if (previousRoles != resultingRoles)
        {
            sendRolesChangedEmail(appUser, organization.name, previousRoles, resultingRoles)
        }

        if (wasActive && !appUser.isActive)
        {
            sendOrganizationMemberDeactivatedEmail(appUser, organization.name)
        }
        else if (!wasActive && appUser.isActive)
        {
            sendOrganizationMemberReactivatedEmail(appUser, organization.name)
        }
    }

    @Transactional
    fun deleteAppUser(organizationId: String?, appUserId: String?, adminApprovalContext: AdminApprovalContext)
    {
        if (organizationId.isNullOrBlank())
        {
            throw IllegalArgumentException("Organization ID cannot be null or blank")
        }
        val targetOrgId = try { UUID.fromString(organizationId) }
            catch (e: IllegalArgumentException) { throw IllegalArgumentException("Invalid organization ID") }
        if (authTokenContext.authToken.appUser?.id?.let { userRoleService.isOrgAdminIn(it, targetOrgId) } != true)
        {
            throw IllegalArgumentException("User does not have permission to remove members from this organization")
        }

        adminActionGuardService.enforce(
            action = "ORG_APP_USER_DELETE",
            actorId = authTokenContext.authToken.appUser?.id,
            context = adminApprovalContext,
        )

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

        // First remove the app user from all organization groups (new principal_group model).
        organizationGroupService.removeUserFromOrganizationGroups(organization.id, appUserUuid)

        // Then remove the app user's membership of the organization (replaces the retired
        // org→users join). The membership row must go before the user is hard-deleted below.
        organizationMembershipService.removeMember(appUserUuid, organization.id)

        sendOrganizationMemberRemovedEmail(appUser, organization.name)

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

        return organizationMembershipService.isMember(appUser.id, organization.id)
                // Cannot delete the org's last enabled administrator (min-admins invariant).
                && !organizationMembershipService.isLastActiveAdmin(appUser.id, organization.id)
                && appUserService.hasLinkedExchanges(appUser.id) == false
    }

    private fun appUserSnapshot(appUser: AppUser): String
    {
        return "id=${appUser.id};email=${appUser.email};isActive=${appUser.isActive};firstName=${appUser.person?.firstName};lastName=${appUser.person?.lastName}"
    }

    private fun enforceOrganizationUserCap(organization: com.docuhyphen.app.api.model.entity.Organization)
    {
        val policy = organizationSubscriptionPolicyRepository.findByOrganizationId(organization.id)
        val tierCode = policy?.tierCode ?: PlatformOrganizationSubscriptionPolicyService.FREE_TIER_CODE
        val maxUsers = policy?.maxUsers
            ?: if (tierCode.equals(PlatformOrganizationSubscriptionPolicyService.FREE_TIER_CODE, ignoreCase = true))
                PlatformOrganizationSubscriptionPolicyService.FREE_TIER_MAX_USERS
            else null
        if (maxUsers == null)
        {
            return
        }

        val activeUsers = organizationMembershipService.membersOf(organization.id)
            .count { it.isActive && it.deprovisionedAt == null }.toLong()
        if (activeUsers >= maxUsers)
        {
            throw IllegalArgumentException("Organization user limit reached for $tierCode tier. Limit is $maxUsers, current active users are $activeUsers.")
        }
    }

    private fun actorLabel(): String
    {
        val actor = authTokenContext.authToken.appUser ?: return "an administrator"
        return actor.person?.let { "${it.firstName} ${it.lastName}" } ?: actor.email
    }

    private fun sendOrganizationMemberAddedEmail(
        appUser: AppUser,
        organizationName: String,
        roles: Set<OrganizationRoleName>,
        isNewUser: Boolean,
        temporaryPassword: String? = null,
        temporaryPasswordExpiry: Timestamp? = null,
    )
    {
        try
        {
            val body = emailTemplateService.renderOrganizationMemberAddedEmail(
                firstName = appUser.person?.firstName ?: "there",
                organizationName = organizationName,
                role = roles.map { it.name }.sorted().joinToString(", "),
                addedBy = actorLabel(),
                isNewUser = isNewUser,
                temporaryPassword = temporaryPassword,
                temporaryPasswordExpiresAt = temporaryPasswordExpiry?.toInstant()?.let(TEMP_PASSWORD_EXPIRY_FORMATTER::format),
            )
            emailService.sendEmail(
                to = appUser.email,
                subject = "${configurationService.emailSubjectTitle} | Added to $organizationName",
                body = body,
                useHtml = true,
            )
        }
        catch (e: Exception)
        {
            logger.error("Failed to send org-member-added email to {}", appUser.email, e)
        }
    }

    private fun applyTemporaryPassword(appUser: AppUser, temporaryPassword: String, expiresAt: Timestamp)
    {
        val passwordSalt = authenticationService.generatePasswordSalt()
        appUser.password = authenticationService.hashPassword(temporaryPassword, passwordSalt)
        appUser.passwordSalt = Base64.getEncoder().encodeToString(passwordSalt.toByteArray())
        appUser.isPasswordTemporary = true
        appUser.temporaryPasswordExpiresAt = expiresAt
    }

    private fun generateTemporaryPassword(length: Int = TEMP_PASSWORD_LENGTH): String
    {
        val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val lower = "abcdefghijkmnopqrstuvwxyz"
        val digits = "23456789"
        val symbols = "!@#$%*_-"
        val all = upper + lower + digits + symbols
        val random = java.security.SecureRandom()

        val required = mutableListOf(
            upper[random.nextInt(upper.length)],
            lower[random.nextInt(lower.length)],
            digits[random.nextInt(digits.length)],
            symbols[random.nextInt(symbols.length)],
        )

        while (required.size < length)
        {
            required.add(all[random.nextInt(all.length)])
        }

        for (i in required.size - 1 downTo 1)
        {
            val j = random.nextInt(i + 1)
            val tmp = required[i]
            required[i] = required[j]
            required[j] = tmp
        }

        return required.joinToString("")
    }

    private fun sendOrganizationMemberRemovedEmail(appUser: AppUser, organizationName: String)
    {
        try
        {
            val body = emailTemplateService.renderOrganizationMemberRemovedEmail(
                firstName = appUser.person?.firstName ?: "there",
                organizationName = organizationName,
                removedBy = actorLabel(),
            )
            emailService.sendEmail(
                to = appUser.email,
                subject = "${configurationService.emailSubjectTitle} | Removed from $organizationName",
                body = body,
                useHtml = true,
            )
        }
        catch (e: Exception)
        {
            logger.error("Failed to send org-member-removed email to {}", appUser.email, e)
        }
    }

    private fun sendOrganizationMemberDeactivatedEmail(appUser: AppUser, organizationName: String)
    {
        try
        {
            val body = emailTemplateService.renderOrganizationMemberDeactivatedEmail(
                firstName = appUser.person?.firstName ?: "there",
                organizationName = organizationName,
                deactivatedBy = actorLabel(),
            )
            emailService.sendEmail(
                to = appUser.email,
                subject = "${configurationService.emailSubjectTitle} | Access deactivated for $organizationName",
                body = body,
                useHtml = true,
            )
        }
        catch (e: Exception)
        {
            logger.error("Failed to send org-member-deactivated email to {}", appUser.email, e)
        }
    }

    private fun sendOrganizationMemberReactivatedEmail(appUser: AppUser, organizationName: String)
    {
        try
        {
            val body = emailTemplateService.renderOrganizationMemberReactivatedEmail(
                firstName = appUser.person?.firstName ?: "there",
                organizationName = organizationName,
                reactivatedBy = actorLabel(),
            )
            emailService.sendEmail(
                to = appUser.email,
                subject = "${configurationService.emailSubjectTitle} | Access reactivated for $organizationName",
                body = body,
                useHtml = true,
            )
        }
        catch (e: Exception)
        {
            logger.error("Failed to send org-member-reactivated email to {}", appUser.email, e)
        }
    }

    private fun sendRolesChangedEmail(
        appUser: AppUser,
        organizationName: String,
        oldRoles: Set<OrganizationRoleName>,
        newRoles: Set<OrganizationRoleName>,
    )
    {
        try
        {
            val body = emailTemplateService.renderRoleChangedEmail(
                firstName = appUser.person?.firstName ?: "there",
                organizationName = organizationName,
                oldRole = oldRoles.map { it.name }.sorted().joinToString(", ").ifBlank { "NONE" },
                newRole = newRoles.map { it.name }.sorted().joinToString(", "),
                changedBy = actorLabel(),
            )
            emailService.sendEmail(
                to = appUser.email,
                subject = "${configurationService.emailSubjectTitle} | Your role was changed",
                body = body,
                useHtml = true,
            )
        }
        catch (e: Exception)
        {
            logger.error("Failed to send roles-changed email to {}", appUser.email, e)
        }
    }

    private fun withParticipationRole(roles: Set<OrganizationRoleName>): Set<OrganizationRoleName>
    {
        if (roles.isEmpty() || roles == setOf(OrganizationRoleName.ORG_GUEST)) return roles
        return roles + OrganizationRoleName.ORG_MEMBER
    }

    private fun Set<OrganizationRoleName>.hasAdminRole(): Boolean =
        OrganizationRoleName.ORG_ADMIN in this || OrganizationRoleName.ORG_OWNER in this
}
