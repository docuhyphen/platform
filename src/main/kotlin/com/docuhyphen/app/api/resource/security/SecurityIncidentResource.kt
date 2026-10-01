package com.docuhyphen.app.api.resource.security

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.SecurityIncidentResponse
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.security.SecurityIncidentService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/auth/security-incidents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SecurityIncidentResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val securityIncidentService: SecurityIncidentService,
    private val userRoleService: UserRoleService,
)
{
    @GET
    fun list(
        @QueryParam("limit") @DefaultValue("25") limit: Int,
        @QueryParam("incidentType") incidentType: String?,
    ): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        if (!userRoleService.isAppAdmin(actor.id))
        {
            return Response.status(Response.Status.FORBIDDEN).entity(ResponseError("Insufficient privileges")).build()
        }

        val incidents = securityIncidentService.findRecent(limit = limit, incidentType = incidentType)
        val response = incidents.map {
            SecurityIncidentResponse(
                id = it.id.toString(),
                incidentType = it.incidentType,
                severity = it.severity,
                actorId = it.actorId?.toString(),
                requestId = it.requestId,
                details = it.details,
                createdDate = it.createdDate.toInstant().toString(),
            )
        }

        return Response.ok(response).build()
    }
}

