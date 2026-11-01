package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.exception.RequestValidationException
import com.docuhyphen.app.api.model.dto.PublishedExchangeGroupDto
import com.docuhyphen.app.api.resource.RequestGuards
import com.docuhyphen.app.api.resource.exchange.operations.PublishedExchangeGroupResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.organization.TrustedExternalGroupQueryService
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.core.GenericEntity
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class PublishedExchangeGroupResource @Inject constructor(
    private val queryService: TrustedExternalGroupQueryService,
) : PublishedExchangeGroupResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PublishedExchangeGroupResource::class.java)
    }

    override fun list(targetOrganizationId: String): Response
    {
        return try
        {
            val organizationId = RequestGuards.uuid(targetOrganizationId, "Target organization id is invalid")
            val groups = queryService.listPublishedGroups(organizationId)
            Response.ok(
                object : GenericEntity<List<PublishedExchangeGroupDto>>(groups)
                {},
            ).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is RequestValidationException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing published Exchange groups", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while listing published Exchange groups"))
                        .build()
                }
            }
        }
    }
}
