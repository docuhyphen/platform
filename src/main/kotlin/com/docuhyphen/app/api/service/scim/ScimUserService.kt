package com.docuhyphen.app.api.service.scim

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.ScimDtoMapper
import com.docuhyphen.app.api.model.dto.ScimListResponse
import com.docuhyphen.app.api.model.dto.ScimPatchRequest
import com.docuhyphen.app.api.model.dto.ScimUser
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.model.entity.Person
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.UserSessionService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.identity.OrganizationIdentityPolicyService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import com.docuhyphen.app.api.service.user.AppUserService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class ScimUserService @Inject constructor(
    private val configurationService: ConfigurationService,
    private val appUserService: AppUserService,
    private val userSessionService: UserSessionService,
    private val authAuditService: AuthAuditService,
    private val userRoleService: UserRoleService,
    private val organizationIdentityPolicyService: OrganizationIdentityPolicyService,
    private val organizationMembershipService: OrganizationMembershipService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ScimUserService::class.java)
        private const val DEFAULT_PAGE_SIZE = 100
        private const val MAX_PAGE_SIZE = 200
        private const val ACTOR_ROLE = "SCIM"
        private const val TARGET_TYPE = "AppUser"
        private val EQUALITY_FILTER = Regex("""(?i)(userName|email)\s+eq\s+"([^"]+)"""")
    }

    fun list(authorization: String?, filter: String?, startIndex: Int?, count: Int?): ScimListResponse<ScimUser>
    {
        authorize(authorization)

        val pageSize = (count ?: DEFAULT_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE)
        val offset = (startIndex ?: 1).coerceAtLeast(1) - 1

        val provisionable = appUserService.listAll().filter { !userRoleService.isAppAdmin(it.id) }
        val filtered = applyFilter(provisionable, filter)
        val page = filtered.drop(offset).take(pageSize)

        return ScimListResponse(
            totalResults = filtered.size,
            startIndex = offset + 1,
            itemsPerPage = page.size,
            resources = page.map(ScimDtoMapper::toScim),
        )
    }

    fun getById(authorization: String?, id: String): ScimUser
    {
        authorize(authorization)
        return ScimDtoMapper.toScim(requireUser(id))
    }

    @Transactional
    fun create(authorization: String?, payload: ScimUser): ScimUser
    {
        authorize(authorization)

        val email = primaryEmail(payload)
        if (appUserService.findByEmail(email) != null)
        {
            throw ScimRequestException.conflict("User already exists", scimType = "uniqueness")
        }

        val person = Person().apply {
            this.firstName = payload.name?.givenName
            this.lastName = payload.name?.familyName
        }
        val newUser = AppUser().apply {
            this.email = email
            this.isActive = payload.active
            this.emailVerificationComplete = true
            this.person = person
        }
        val saved = appUserService.create(newUser)

        organizationIdentityPolicyService.resolveOrganizationForEmail(email)?.let { organization ->
            try
            {
                organizationMembershipService.assignOrgRole(
                    appUserId = saved.id,
                    organizationId = organization.id,
                    role = OrganizationRoleName.ORG_MEMBER,
                    isPrimary = true,
                )
            }
            catch (exception: SubscriptionDenialException)
            {
                logger.error("Subscription denied while SCIM created an organization membership", exception)
                throw exception
            }
        }

        emitSuccess("SCIM_USER_CREATE", saved.id)
        return ScimDtoMapper.toScim(saved)
    }

    @Transactional
    fun replace(authorization: String?, id: String, payload: ScimUser): ScimUser
    {
        authorize(authorization)
        val user = requireUser(id)

        val email = primaryEmail(payload)
        if (payload.active && (!user.isActive || user.deprovisionedAt != null))
        {
            enforceSeatsForAccountActivation(user.id)
        }
        user.email = email
        user.isActive = payload.active
        if (payload.active)
        {
            user.deprovisionedAt = null
        }
        user.person?.let { person ->
            person.firstName = payload.name?.givenName ?: person.firstName
            person.lastName = payload.name?.familyName ?: person.lastName
        }
        if (!user.isActive && user.deprovisionedAt == null)
        {
            user.deprovisionedAt = Timestamp.from(Instant.now())
            userSessionService.revokeAllUserSessions(user.id, RevocationReasonCode.DEPROVISIONED)
        }
        appUserService.update(user)
        emitSuccess("SCIM_USER_REPLACE", user.id)
        return ScimDtoMapper.toScim(user)
    }

    @Transactional
    fun patch(authorization: String?, id: String, payload: ScimPatchRequest): ScimUser
    {
        authorize(authorization)
        val user = requireUser(id)

        for (operation in payload.Operations)
        {
            val path = operation.path?.lowercase() ?: continue
            if (path != "active" || !operation.op.equals("replace", ignoreCase = true))
            {
                throw ScimRequestException.notImplemented("PATCH path '$path' not supported; use PUT")
            }
            val newActive = operation.value?.toString()?.trim('"')?.toBooleanStrictOrNull() ?: continue
            if (!newActive && user.isActive)
            {
                user.isActive = false
                user.deprovisionedAt = Timestamp.from(Instant.now())
                userSessionService.revokeAllUserSessions(user.id, RevocationReasonCode.DEPROVISIONED)
            }
            else if (newActive && (!user.isActive || user.deprovisionedAt != null))
            {
                enforceSeatsForAccountActivation(user.id)
                user.isActive = true
                user.deprovisionedAt = null
            }
            appUserService.update(user)
        }

        emitSuccess("SCIM_USER_PATCH", user.id)
        return ScimDtoMapper.toScim(user)
    }

    @Transactional
    fun deprovision(authorization: String?, id: String)
    {
        authorize(authorization)
        val user = requireUser(id)

        user.isActive = false
        user.deprovisionedAt = Timestamp.from(Instant.now())
        appUserService.update(user)
        userSessionService.revokeAllUserSessions(user.id, RevocationReasonCode.DEPROVISIONED)
        authAuditService.emit(
            action = "SCIM_USER_DEPROVISION",
            outcome = "SUCCESS",
            actorRole = ACTOR_ROLE,
            targetType = TARGET_TYPE,
            targetId = user.id.toString(),
            reasonCode = RevocationReasonCode.DEPROVISIONED,
        )
    }

    private fun authorize(authorization: String?)
    {
        val expected = configurationService.getScimBearerToken()
        if (expected.isBlank())
        {
            throw ScimRequestException.serviceUnavailable("SCIM endpoint not configured")
        }
        val token = authorization?.removePrefix("Bearer ")?.trim()
        if (token.isNullOrBlank() || !constantTimeEquals(token, expected))
        {
            throw ScimRequestException.unauthorized("Invalid SCIM credentials")
        }
    }

    private fun constantTimeEquals(left: String, right: String): Boolean
    {
        return MessageDigest.isEqual(left.toByteArray(Charsets.UTF_8), right.toByteArray(Charsets.UTF_8))
    }

    private fun requireUser(id: String): AppUser
    {
        val uuid = runCatching { UUID.fromString(id) }.getOrNull()
            ?: throw ScimRequestException.badRequest("Invalid user id")
        return appUserService.getById(uuid)
            ?: throw ScimRequestException.notFound("User not found")
    }

    private fun primaryEmail(payload: ScimUser): String =
        (payload.emails.firstOrNull { it.primary }?.value ?: payload.userName).lowercase()

    private fun enforceSeatsForAccountActivation(appUserId: UUID)
    {
        try
        {
            organizationMembershipService.enforceSeatsForAccountActivation(appUserId)
        }
        catch (exception: SubscriptionDenialException)
        {
            logger.error("Subscription denied while SCIM reactivated an organization member", exception)
            throw exception
        }
    }

    private fun applyFilter(users: List<AppUser>, filter: String?): List<AppUser>
    {
        if (filter.isNullOrBlank()) return users
        val match = EQUALITY_FILTER.find(filter) ?: return users
        val target = match.groupValues[2].lowercase()
        return users.filter { it.email.lowercase() == target }
    }

    private fun emitSuccess(action: String, appUserId: UUID)
    {
        authAuditService.emit(
            action = action,
            outcome = "SUCCESS",
            actorRole = ACTOR_ROLE,
            targetType = TARGET_TYPE,
            targetId = appUserId.toString(),
        )
    }
}
