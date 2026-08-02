package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.model.dto.PublishedExchangeGroupDto
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.organization.TrustedExternalGroupQueryService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.GenericEntity
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import java.util.UUID

@Path("organizations/{targetOrganizationId}/published-exchange-groups")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class PublishedExchangeGroupResource @Inject constructor(
    private val queryService: TrustedExternalGroupQueryService,
)
{
    @GET
    fun list(@PathParam("targetOrganizationId") targetOrganizationId: String): Response
    {
        val organizationId = runCatching { UUID.fromString(targetOrganizationId) }
            .getOrElse {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError("Target organization id is invalid"))
                    .build()
            }
        val groups = queryService.listPublishedGroups(organizationId)
        return Response.ok(
            object : GenericEntity<List<PublishedExchangeGroupDto>>(groups) {},
        ).build()
    }
}
