package com.docuhyphen.app.api.resource.identity

import com.docuhyphen.app.api.exception.OrganizationTrustException
import com.docuhyphen.app.api.resource.identity.operations.ExternalIdentityResolutionResourceOperations
import com.docuhyphen.app.api.resource.model.ExternalIdentityResolutionRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.identity.ExternalIdentityResolutionService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.util.*

class ExternalIdentityResolutionResource @Inject constructor(
    private val resolutionService: ExternalIdentityResolutionService,
) : ExternalIdentityResolutionResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExternalIdentityResolutionResource::class.java)
    }

    override fun resolve(
        targetOrganizationId: String,
        request: ExternalIdentityResolutionRequest,
    ): Response
    {
        return try
        {
            Response.ok(
                resolutionService.resolve(UUID.fromString(targetOrganizationId), request.email),
            ).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is OrganizationTrustException -> throw exception

                else ->
                {
                    logger.error("Error resolving external identity", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to resolve external identity"))
                        .build()
                }
            }
        }
    }
}
