package com.docuhyphen.app.api.resource.scim

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.Person
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.repository.AppUserRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.OrganizationIdentityPolicyService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.auth.UserSessionService
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * SCIM 2.0 (RFC 7644) User endpoint,  provisioning subset.
 *
 * Auth: a static bearer token configured in [ConfigurationService.getScimBearerToken].
 * The token is a long-lived shared secret given to the upstream IdP (Okta, Azure AD, etc.).
 *
 * Implemented operations: list, get by id, create, replace, partial-update (PATCH active),
 * delete (= deprovision: deactivate + revoke all sessions).
 *
 * Out of scope here: SCIM Groups, complex PATCH paths, custom enterprise extensions.
 */
@Path("/scim/v2/Users")
@Produces(MediaType.APPLICATION_JSON, "application/scim+json")
@Consumes(MediaType.APPLICATION_JSON, "application/scim+json")
class ScimUserResource @Inject constructor(
    private val configurationService: ConfigurationService,
    private val appUserService: AppUserService,
    private val appUserRepository: AppUserRepository,
    private val userSessionService: UserSessionService,
    private val authAuditService: AuthAuditService,
    private val userRoleService: com.docuhyphen.app.api.service.auth.UserRoleService,
    private val organizationIdentityPolicyService: OrganizationIdentityPolicyService,
    private val organizationMembershipService: OrganizationMembershipService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ScimUserResource::class.java)
    }

    @GET
    fun list(
        @HeaderParam("Authorization") authorization: String?,
        @QueryParam("filter") filter: String?,
        @QueryParam("startIndex") startIndex: Int?,
        @QueryParam("count") count: Int?,
    ): Response
    {
        authorize(authorization)?.let { return it }

        val pageSize = (count ?: 100).coerceIn(1, 200)
        val offset = (startIndex ?: 1).coerceAtLeast(1) - 1

        val all = appUserRepository.findAll().filter { !userRoleService.isAppAdmin(it.id) }
        val filtered = applyFilter(all, filter)
        val page = filtered.drop(offset).take(pageSize)

        return Response.ok(
            ScimListResponse(
                totalResults = filtered.size,
                startIndex = offset + 1,
                itemsPerPage = page.size,
                resources = page.map { it.toScim() },
            )
        ).build()
    }

    @GET
    @Path("/{id}")
    fun getById(
        @HeaderParam("Authorization") authorization: String?,
        @PathParam("id") id: String,
    ): Response
    {
        authorize(authorization)?.let { return it }

        val uuid = runCatching { UUID.fromString(id) }.getOrNull()
            ?: return error(Response.Status.BAD_REQUEST, "Invalid user id")
        val user = appUserService.getById(uuid)
            ?: return error(Response.Status.NOT_FOUND, "User not found")
        return Response.ok(user.toScim()).build()
    }

    @POST
    fun create(
        @HeaderParam("Authorization") authorization: String?,
        payload: ScimUser,
    ): Response
    {
        authorize(authorization)?.let { return it }

        val email = (payload.emails.firstOrNull { it.primary }?.value ?: payload.userName).lowercase()
        if (appUserService.findByEmail(email) != null)
        {
            return error(Response.Status.CONFLICT, "User already exists", scimType = "uniqueness")
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

        // Best-effort org membership: the SCIM endpoint uses a global token (no org context),
        // so the provisioning org is resolved from the email domain and recorded in
        // organization_membership.
        organizationIdentityPolicyService.resolveOrganizationForEmail(email)?.let { organization ->
            organizationMembershipService.assignOrgRole(
                appUserId = saved.id,
                organizationId = organization.id,
                role = OrganizationRoleName.ORG_MEMBER,
                isPrimary = true,
            )
        }

        authAuditService.emit(
            action = "SCIM_USER_CREATE",
            outcome = "SUCCESS",
            actorRole = "SCIM",
            targetType = "AppUser",
            targetId = saved.id.toString(),
        )
        return Response.status(Response.Status.CREATED).entity(saved.toScim()).build()
    }

    @PUT
    @Path("/{id}")
    fun replace(
        @HeaderParam("Authorization") authorization: String?,
        @PathParam("id") id: String,
        payload: ScimUser,
    ): Response
    {
        authorize(authorization)?.let { return it }

        val uuid = runCatching { UUID.fromString(id) }.getOrNull()
            ?: return error(Response.Status.BAD_REQUEST, "Invalid user id")
        val user = appUserService.getById(uuid)
            ?: return error(Response.Status.NOT_FOUND, "User not found")

        val email = (payload.emails.firstOrNull { it.primary }?.value ?: payload.userName).lowercase()
        user.email = email
        user.isActive = payload.active
        user.person?.let { p ->
            p.firstName = payload.name?.givenName ?: p.firstName
            p.lastName = payload.name?.familyName ?: p.lastName
        }
        if (!user.isActive && user.deprovisionedAt == null)
        {
            user.deprovisionedAt = Timestamp.from(Instant.now())
            userSessionService.revokeAllUserSessions(user.id, RevocationReasonCode.DEPROVISIONED)
        }
        appUserService.update(user)
        authAuditService.emit(
            action = "SCIM_USER_REPLACE",
            outcome = "SUCCESS",
            actorRole = "SCIM",
            targetType = "AppUser",
            targetId = user.id.toString(),
        )
        return Response.ok(user.toScim()).build()
    }

    @PATCH
    @Path("/{id}")
    fun patch(
        @HeaderParam("Authorization") authorization: String?,
        @PathParam("id") id: String,
        payload: ScimPatchRequest,
    ): Response
    {
        authorize(authorization)?.let { return it }

        val uuid = runCatching { UUID.fromString(id) }.getOrNull()
            ?: return error(Response.Status.BAD_REQUEST, "Invalid user id")
        val user = appUserService.getById(uuid)
            ?: return error(Response.Status.NOT_FOUND, "User not found")

        // Minimal PATCH support: only the common `active` toggle for deprovisioning.
        // Anything more complex returns 501 to signal the IdP to fall back to PUT.
        for (op in payload.Operations)
        {
            val path = op.path?.lowercase() ?: continue
            if (path == "active" && op.op.equals("replace", ignoreCase = true))
            {
                val newActive = op.value?.toString()?.trim('"')?.toBooleanStrictOrNull() ?: continue
                if (!newActive && user.isActive)
                {
                    user.isActive = false
                    user.deprovisionedAt = Timestamp.from(Instant.now())
                    userSessionService.revokeAllUserSessions(user.id, RevocationReasonCode.DEPROVISIONED)
                }
                else if (newActive && !user.isActive)
                {
                    user.isActive = true
                    user.deprovisionedAt = null
                }
                appUserService.update(user)
            }
            else
            {
                return error(Response.Status.NOT_IMPLEMENTED, "PATCH path '$path' not supported; use PUT")
            }
        }

        authAuditService.emit(
            action = "SCIM_USER_PATCH",
            outcome = "SUCCESS",
            actorRole = "SCIM",
            targetType = "AppUser",
            targetId = user.id.toString(),
        )
        return Response.ok(user.toScim()).build()
    }

    @DELETE
    @Path("/{id}")
    fun delete(
        @HeaderParam("Authorization") authorization: String?,
        @PathParam("id") id: String,
    ): Response
    {
        authorize(authorization)?.let { return it }

        val uuid = runCatching { UUID.fromString(id) }.getOrNull()
            ?: return error(Response.Status.BAD_REQUEST, "Invalid user id")
        val user = appUserService.getById(uuid)
            ?: return error(Response.Status.NOT_FOUND, "User not found")

        // SCIM DELETE = deprovision (NOT hard-delete). Records are preserved for audit;
        // sessions are revoked so the deprovisioned user can no longer access the platform.
        user.isActive = false
        user.deprovisionedAt = Timestamp.from(Instant.now())
        appUserService.update(user)
        userSessionService.revokeAllUserSessions(user.id, RevocationReasonCode.DEPROVISIONED)
        authAuditService.emit(
            action = "SCIM_USER_DEPROVISION",
            outcome = "SUCCESS",
            actorRole = "SCIM",
            targetType = "AppUser",
            targetId = user.id.toString(),
            reasonCode = RevocationReasonCode.DEPROVISIONED,
        )
        return Response.noContent().build()
    }

    /**
     * Validates the static SCIM bearer token. Returns `null` when the caller is authorized,
     * or a ready-to-return error [Response] otherwise:
     *   * 503 if no SCIM token is configured (endpoint effectively disabled),
     *   * 401 if the Authorization header is missing/malformed or the token doesn't match.
     * Comparison is constant-time to avoid leaking the secret via timing.
     */
    private fun authorize(authorization: String?): Response?
    {
        val expected = configurationService.getScimBearerToken()
        if (expected.isBlank())
        {
            return error(Response.Status.SERVICE_UNAVAILABLE, "SCIM endpoint not configured")
        }
        val token = authorization?.removePrefix("Bearer ")?.trim()
        if (token.isNullOrBlank() || !constantTimeEquals(token, expected))
        {
            return error(Response.Status.UNAUTHORIZED, "Invalid SCIM credentials")
        }
        return null
    }

    private fun constantTimeEquals(a: String, b: String): Boolean
    {
        return java.security.MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }

    private fun applyFilter(users: List<AppUser>, filter: String?): List<AppUser>
    {
        if (filter.isNullOrBlank()) return users
        // Minimal SCIM filter parser: supports `userName eq "x"` and `email eq "x"`.
        val eqMatch = Regex("""(?i)(userName|email)\s+eq\s+"([^"]+)"""").find(filter)
        if (eqMatch != null)
        {
            val target = eqMatch.groupValues[2].lowercase()
            return users.filter { it.email.lowercase() == target }
        }
        return users
    }

    private fun error(status: Response.Status, detail: String, scimType: String? = null): Response
    {
        return Response.status(status)
            .entity(ScimError(status = status.statusCode.toString(), detail = detail, scimType = scimType))
            .build()
    }

    private fun AppUser.toScim(): ScimUser = ScimUser(
        id = this.id.toString(),
        userName = this.email,
        name = ScimName(
            givenName = this.person?.firstName,
            familyName = this.person?.lastName,
            formatted = listOfNotNull(this.person?.firstName, this.person?.lastName).joinToString(" ").ifBlank { null },
        ),
        emails = listOf(ScimEmail(value = this.email, primary = true, type = "work")),
        active = this.isActive,
        meta = ScimMeta(
            created = this.createdDate.toInstant().toString(),
            lastModified = this.createdDate.toInstant().toString(),
            location = "/scim/v2/Users/${this.id}",
        ),
    )
}
