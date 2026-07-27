package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.exception.LastAppAdminException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppRoleName
import com.docuhyphen.app.api.resource.model.AppAdminDto
import com.docuhyphen.app.api.resource.model.AppUserSearchResultDto
import com.docuhyphen.app.api.resource.model.GrantAppAdminRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.AppRoleAssignmentService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.GenericEntity
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * App Admin (APP-scope role) provisioning. Every route requires the caller to already
 * be an App Admin, resolved from the authenticated session, never the request body. App roles
 * are additive, so an admin keeps all their normal org/group/personal grants.
 */
@Path("/admin/roles")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class AppRoleResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val appRoleAssignmentService: AppRoleAssignmentService,
    private val appUserService: AppUserService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AppRoleResource::class.java)
    }

    @GET
    @Path("/app-admins")
    fun listAppAdmins(): Response = guarded {
        val admins = appRoleAssignmentService.listAppAdmins().map { ra ->
            val appUser = ra.appUserId?.let { appUserService.getById(it) }
            AppAdminDto(
                assignmentId = ra.id.toString(),
                appUserId = ra.appUserId?.toString(),
                email = appUser?.email,
                firstName = appUser?.person?.firstName,
                lastName = appUser?.person?.lastName,
                grantedByAppUserId = ra.grantedByAppUserId?.toString(),
                grantedAt = ra.grantedAt.toInstant().toString(),
            )
        }
        Response.ok(object : GenericEntity<List<AppAdminDto>>(admins) {}).build()
    }

    @POST
    @Path("/app-admins")
    fun grantAppAdmin(request: GrantAppAdminRequest): Response = guarded {
        val targetId = request.appUserId?.trim()?.takeIf { it.isNotBlank() }
            ?.let { runCatching { UUID.fromString(it) }.getOrElse { throw IllegalArgumentException("Invalid appUserId") } }
            ?: throw IllegalArgumentException("appUserId is required")

        val assignment = appRoleAssignmentService.grantAppRole(targetId, AppRoleName.APP_ADMIN, actorId())
        Response.status(CREATED)
            .entity(AppAdminDto(assignmentId = assignment.id.toString(), appUserId = assignment.appUserId?.toString()))
            .build()
    }

    @DELETE
    @Path("/app-admins/{assignmentId}")
    fun revokeAppAdmin(@PathParam("assignmentId") assignmentId: String): Response = guarded {
        val id = runCatching { UUID.fromString(assignmentId) }
            .getOrElse { throw IllegalArgumentException("Invalid assignmentId") }
        appRoleAssignmentService.revokeAppRole(id, actorId())
        Response.status(NO_CONTENT).build()
    }

    /**
     * Global app-user search for the App Admins picker. App-admin is a global
     * role, so the picker must reach users outside the caller's org. Guarded by the same
     * `isAppAdmin` check as the rest of this resource, an attacker without app-admin gets a
     * 403 before any DB hit.
     */
    @GET
    @Path("/app-admin-candidates")
    fun searchAppAdminCandidates(
        @QueryParam("q") q: String?,
        @QueryParam("limit") @DefaultValue("20") limit: Int,
    ): Response = guarded {
        val query = q?.trim().orEmpty()
        if (query.length < 2)
        {
            return@guarded Response.ok(object : GenericEntity<List<AppUserSearchResultDto>>(emptyList()) {}).build()
        }
        val hits = appUserService.searchActiveUsers(query, limit).map { u ->
            AppUserSearchResultDto(
                id = u.id.toString(),
                email = u.email,
                firstName = u.person?.firstName,
                lastName = u.person?.lastName,
            )
        }
        Response.ok(object : GenericEntity<List<AppUserSearchResultDto>>(hits) {}).build()
    }

    // -------------------------------------------------------------------------

    private fun actorId(): UUID? = authTokenContext.authToken.appUser?.id

    /** Require an authenticated App Admin, then run [block] with unified error mapping. */
    private fun guarded(block: () -> Response): Response
    {
        return try
        {
            val actor = authTokenContext.authToken.appUser
                ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Authentication required")).build()

            appRoleAssignmentService.requireAppAdmin(actor.id)
            block()
        }
        catch (exception: Exception)
        {
            if (exception is WebApplicationException) throw exception
            logger.error("Error in app-role endpoint", exception)
            when (exception)
            {
                is LastAppAdminException ->
                    Response.status(CONFLICT).entity(ResponseError(exception.message)).build()
                is AppUserNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()
                is SecurityException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()
                else ->
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while managing app roles")).build()
            }
        }
    }
}
