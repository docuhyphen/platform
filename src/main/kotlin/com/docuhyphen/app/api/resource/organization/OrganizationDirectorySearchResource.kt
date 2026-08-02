package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.model.dto.OrganizationDirectoryEntryDto
import com.docuhyphen.app.api.resource.model.OrganizationDirectorySearchRequest
import com.docuhyphen.app.api.service.organization.OrganizationDirectorySearchService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.GenericEntity
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("organization-directory-searches")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class OrganizationDirectorySearchResource @Inject constructor(
    private val searchService: OrganizationDirectorySearchService,
)
{
    @POST
    fun search(
        request: OrganizationDirectorySearchRequest,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        val organizations = searchService.search(request.query, requestId)
        return Response.ok(
            object : GenericEntity<List<OrganizationDirectoryEntryDto>>(organizations) {},
        ).build()
    }
}
