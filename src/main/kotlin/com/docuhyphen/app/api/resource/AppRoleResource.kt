package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.exception.LastAppAdminException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.RoleName
import com.docuhyphen.app.api.resource.model.AppAdminDto
import com.docuhyphen.app.api.resource.model.GrantAppAdminRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.RoleAssignmentService
import com.docuhyphen.app.api.service.auth.UserRoleService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * App Admin (APP-scope role) provisioning (Plan 04). Every route requires the caller to already
 * be an App Admin — resolved from the authenticated session, never the request body. App roles
 * are additive, so an admin keeps all their normal org/group/personal grants.
 */
@Path("admin/roles")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class AppRoleResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val userRoleService: UserRoleService,
    private val roleAssignmentService: RoleAssignmentService,
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
        val admins = roleAssignmentService.listAppAdmins().map { ra ->
            AppAdminDto(
                assignmentId = ra.id.toString(),
                appUserId = ra.appUserId?.toString(),
                email = ra.appUserId?.let { appUserService.getById(it)?.email },
                grantedByAppUserId = ra.grantedByAppUserId?.toString(),
                grantedAt = ra.grantedAt.toInstant().toString(),
            )
        }
        Response.ok(admins).build()
    }

    @POST
    @Path("/app-admins")
    fun grantAppAdmin(request: GrantAppAdminRequest): Response = guarded {
        val targetId = request.appUserId?.trim()?.takeIf { it.isNotBlank() }
            ?.let { runCatching { UUID.fromString(it) }.getOrElse { throw IllegalArgumentException("Invalid appUserId") } }
            ?: throw IllegalArgumentException("appUserId is required")

        val assignment = roleAssignmentService.grantAppRole(targetId, RoleName.APP_ADMIN, actorId())
        Response.status(CREATED)
            .entity(AppAdminDto(assignmentId = assignment.id.toString(), appUserId = assignment.appUserId?.toString()))
            .build()
    }

    @DELETE
    @Path("/app-admins/{assignmentId}")
    fun revokeAppAdmin(@PathParam("assignmentId") assignmentId: String): Response = guarded {
        val id = runCatching { UUID.fromString(assignmentId) }
            .getOrElse { throw IllegalArgumentException("Invalid assignmentId") }
        roleAssignmentService.revokeAppRole(id, actorId())
        Response.status(NO_CONTENT).build()
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
            if (!userRoleService.isAppAdmin(actor.id))
            {
                return Response.status(FORBIDDEN)
                    .entity(ResponseError("App administrator privilege required")).build()
            }
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
                else ->
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while managing app roles")).build()
            }
        }
    }
}
