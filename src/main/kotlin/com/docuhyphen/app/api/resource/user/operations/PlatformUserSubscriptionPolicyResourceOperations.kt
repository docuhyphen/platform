package com.docuhyphen.app.api.resource.user.operations

import com.docuhyphen.app.api.resource.RequestHeaders
import com.docuhyphen.app.api.resource.model.PlatformUserSubscriptionPolicyRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/platform/users")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface PlatformUserSubscriptionPolicyResourceOperations {
    @GET
    @Path("/subscription-policies")
    fun list(
        @QueryParam("query") query: String?,
        @QueryParam("limit") limit: Int?,
        @QueryParam("offset") offset: Int?,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @GET
    @Path("/{appUserId}/subscription-policy")
    fun get(
        @PathParam("appUserId") appUserId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @PATCH
    @Path("/{appUserId}/subscription-policy")
    fun update(
        @PathParam("appUserId") appUserId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        request: PlatformUserSubscriptionPolicyRequest,
    ): Response
}
