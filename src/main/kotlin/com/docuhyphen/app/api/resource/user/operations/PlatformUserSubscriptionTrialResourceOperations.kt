package com.docuhyphen.app.api.resource.user.operations

import com.docuhyphen.app.api.resource.RequestHeaders
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialConversionRequest
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialEndRequest
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialExtensionRequest
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialStartRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/platform/users/{appUserId}/subscription-trials")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface PlatformUserSubscriptionTrialResourceOperations {
    @POST
    fun start(
        @PathParam("appUserId") appUserId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        request: PlatformSubscriptionTrialStartRequest,
    ): Response

    @PATCH
    @Path("/current")
    fun extend(
        @PathParam("appUserId") appUserId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        request: PlatformSubscriptionTrialExtensionRequest,
    ): Response

    @DELETE
    @Path("/current")
    fun end(
        @PathParam("appUserId") appUserId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        request: PlatformSubscriptionTrialEndRequest,
    ): Response

    @POST
    @Path("/current/conversions")
    fun convert(
        @PathParam("appUserId") appUserId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        request: PlatformSubscriptionTrialConversionRequest,
    ): Response
}
