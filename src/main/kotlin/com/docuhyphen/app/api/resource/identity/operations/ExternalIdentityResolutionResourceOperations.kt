package com.docuhyphen.app.api.resource.identity.operations

import com.docuhyphen.app.api.resource.model.ExternalIdentityResolutionRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("organizations/{targetOrganizationId}/external-identity-resolutions")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface ExternalIdentityResolutionResourceOperations
{
    @POST
    fun resolve(
        @PathParam("targetOrganizationId") targetOrganizationId: String,
        request: ExternalIdentityResolutionRequest,
    ): Response
}
