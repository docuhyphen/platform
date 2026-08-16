package com.docuhyphen.app.api.resource.identity

import com.docuhyphen.app.api.resource.model.ExternalIdentityResolutionRequest
import com.docuhyphen.app.api.service.identity.ExternalIdentityResolutionService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import java.util.UUID

@Path("organizations/{targetOrganizationId}/external-identity-resolutions")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class ExternalIdentityResolutionResource @Inject constructor(
    private val resolutionService: ExternalIdentityResolutionService,
)
{
    @POST
    fun resolve(
        @PathParam("targetOrganizationId") targetOrganizationId: String,
        request: ExternalIdentityResolutionRequest,
    ): Response = Response.ok(
        resolutionService.resolve(UUID.fromString(targetOrganizationId), request.email),
    ).build()
}
