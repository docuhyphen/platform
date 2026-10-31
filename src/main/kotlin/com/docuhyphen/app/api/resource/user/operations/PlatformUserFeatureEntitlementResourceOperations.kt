package com.docuhyphen.app.api.resource.user.operations

import com.docuhyphen.app.api.resource.RequestHeaders
import com.docuhyphen.app.api.resource.model.PlatformUserFeatureEntitlementsUpdateRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/platform/users/{appUserId}/feature-entitlements")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface PlatformUserFeatureEntitlementResourceOperations {
    @GET
    fun get(
        @PathParam("appUserId") appUserId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @PUT
    fun replace(
        @PathParam("appUserId") appUserId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        payload: PlatformUserFeatureEntitlementsUpdateRequest,
    ): Response
}
