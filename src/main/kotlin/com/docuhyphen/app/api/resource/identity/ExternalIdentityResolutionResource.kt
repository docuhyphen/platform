package com.docuhyphen.app.api.resource.identity

import com.docuhyphen.app.api.resource.model.ExternalIdentityResolutionRequest
import com.docuhyphen.app.api.service.identity.ExternalIdentityResolutionService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import java.util.*

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
