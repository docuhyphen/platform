package com.docuhyphen.app.api.resource.application

import com.docuhyphen.app.api.resource.application.operations.ApplicationScopedResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.application.ApplicationScopeProbeService
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class ApplicationScopedResource @Inject constructor(
    private val applicationScopeProbeService: ApplicationScopeProbeService,
) : ApplicationScopedResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ApplicationScopedResource::class.java)
        private const val INTEGRATION_ENDPOINT_GROUP = "integration"
        private const val SERVICE_ENDPOINT_GROUP = "service"
        private const val FAILURE_MESSAGE = "An error occurred while answering the application scope probe"
    }

    override fun integrationPing(): Response
    {
        return try
        {
            Response.ok(applicationScopeProbeService.probe(INTEGRATION_ENDPOINT_GROUP)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                else ->
                {
                    logger.error("Error answering integration scope probe", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun servicePing(): Response
    {
        return try
        {
            Response.ok(applicationScopeProbeService.probe(SERVICE_ENDPOINT_GROUP)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                else ->
                {
                    logger.error("Error answering service scope probe", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }
}
