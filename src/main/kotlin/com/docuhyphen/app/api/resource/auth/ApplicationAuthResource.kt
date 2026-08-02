package com.docuhyphen.app.api.resource.auth

import com.docuhyphen.app.api.resource.model.ApplicationTokenRequest
import com.docuhyphen.app.api.resource.model.ApplicationTokenResponse
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.application.ApplicationService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/auth/application")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ApplicationAuthResource @Inject constructor(
    private val applicationService: ApplicationService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ApplicationAuthResource::class.java)
    }

    @POST
    @Path("/token")
    fun getApplicationToken(
        payload: ApplicationTokenRequest,
    ): Response
    {
        return try
        {
            if (payload.apiKey.isNullOrBlank() || payload.apiSecret.isNullOrBlank())
            {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError("API key and secret are required"))
                    .build()
            }

            val accessToken = applicationService.authenticateAndIssueToken(payload.apiKey, payload.apiSecret)
                ?: return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("Invalid API credentials"))
                    .build()

            Response.ok(ApplicationTokenResponse(accessToken)).build()
        }
        catch (e: Exception)
        {
            logger.error("Error generating application token", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to generate application token"))
                .build()
        }
    }
}
