package com.docuhyphen.app.api.resource.organization.operations

import com.docuhyphen.app.api.resource.RequestHeaders
import com.docuhyphen.app.api.resource.model.PlatformOrganizationSubscriptionPolicyRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/platform/organizations/{organizationId}/subscription-policy")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface PlatformOrganizationSubscriptionPolicyResourceOperations {
    @GET
    fun getEffectivePolicy(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @PUT
    fun upsertPolicy(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        payload: PlatformOrganizationSubscriptionPolicyRequest,
    ): Response
}
