package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.service.organization.TrustedExternalGroupQueryService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
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
    fun list(@PathParam("targetOrganizationId") targetOrganizationId: String): Response =
        Response.ok(queryService.listPublishedGroups(UUID.fromString(targetOrganizationId))).build()
}
