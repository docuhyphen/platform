package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.exception.OrganizationTrustException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.dto.OrganizationDirectoryEntryDto
import com.docuhyphen.app.api.resource.model.OrganizationDirectorySearchRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.organization.operations.OrganizationDirectorySearchResourceOperations
import com.docuhyphen.app.api.service.organization.OrganizationDirectorySearchService
import jakarta.inject.Inject
import jakarta.ws.rs.core.GenericEntity
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class OrganizationDirectorySearchResource @Inject constructor(
    private val searchService: OrganizationDirectorySearchService,
) : OrganizationDirectorySearchResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationDirectorySearchResource::class.java)
    }

    override fun search(
        request: OrganizationDirectorySearchRequest,
        requestId: String?,
    ): Response
    {
        return try
        {
            val organizations = searchService.search(request.query, requestId)
            Response.ok(
                object : GenericEntity<List<OrganizationDirectoryEntryDto>>(organizations)
                {},
            ).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is OrganizationTrustException -> throw exception

                is SubscriptionDenialException -> throw exception

                else ->
                {
                    logger.error("Error searching the organization directory", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }
}
