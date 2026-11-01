package com.docuhyphen.app.api.resource.security

import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.security.operations.SecurityIncidentResourceOperations
import com.docuhyphen.app.api.service.security.SecurityIncidentQueryService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class SecurityIncidentResource @Inject constructor(
    private val securityIncidentQueryService: SecurityIncidentQueryService,
) : SecurityIncidentResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SecurityIncidentResource::class.java)
    }

    override fun list(limit: Int, incidentType: String?): Response
    {
        return try
        {
            Response.ok(securityIncidentQueryService.listRecent(limit = limit, incidentType = incidentType)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing security incidents", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to list security incidents"))
                        .build()
                }
            }
        }
    }
}
