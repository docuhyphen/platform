package com.docuhyphen.app.api.resource.organization.operations

import com.docuhyphen.app.api.resource.RequestHeaders
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/platform/organizations/subscription-policies")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface PlatformOrganizationSubscriptionPolicyCollectionResourceOperations {
    @GET
    fun list(
        @QueryParam("limit") limit: Int?,
        @QueryParam("offset") offset: Int?,
        @QueryParam("tierCode") tierCode: String?,
        @QueryParam("persistedOnly") persistedOnly: Boolean?,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response
}
