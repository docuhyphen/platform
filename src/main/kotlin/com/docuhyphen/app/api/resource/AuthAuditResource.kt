package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.resource.model.AuthAuditEventResponse
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.UserRoleService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/auth/audit-events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AuthAuditResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val authAuditService: AuthAuditService,
    private val userRoleService: UserRoleService,
)
{
    @GET
    fun list(
        @QueryParam("limit") @DefaultValue("25") limit: Int,
        @QueryParam("action") action: String?,
        @QueryParam("outcome") outcome: String?,
        @QueryParam("includeSnapshots") @DefaultValue("false") includeSnapshots: Boolean,
    ): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        if (!userRoleService.isOrgAdmin(actor.id))
        {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(ResponseError("Insufficient privileges"))
                .build()
        }

        val events = authAuditService.findRecent(
            limit = limit,
            action = action?.trim()?.takeIf { it.isNotBlank() },
            outcome = outcome?.trim()?.takeIf { it.isNotBlank() },
            includeSnapshots = includeSnapshots,
        )

        val response = events.map { event ->
            AuthAuditEventResponse(
                id = event.id.toString(),
                actorId = event.actorId?.toString(),
                action = event.action,
                outcome = event.outcome,
                reasonCode = event.reasonCode,
                sessionId = event.sessionId,
                organizationId = event.organizationId?.toString(),
                requestId = event.requestId,
                actionReason = event.actionReason,
                beforeSnapshot = event.beforeSnapshot,
                afterSnapshot = event.afterSnapshot,
                eventHash = event.eventHash,
                prevEventHash = event.prevEventHash,
                createdDate = event.createdDate.toInstant().toString(),
            )
        }

        return Response.ok(response).build()
    }
}

