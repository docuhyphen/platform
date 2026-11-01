package com.docuhyphen.app.api.resource.organization.operations

import com.docuhyphen.app.api.resource.RequestHeaders
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialConversionRequest
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialEndRequest
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialExtensionRequest
import com.docuhyphen.app.api.resource.model.PlatformSubscriptionTrialStartRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/platform/organizations/{organizationId}/subscription-trials")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface PlatformOrganizationSubscriptionTrialResourceOperations {
    @POST
    fun start(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        request: PlatformSubscriptionTrialStartRequest,
    ): Response

    @PATCH
    @Path("/current")
    fun extend(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        request: PlatformSubscriptionTrialExtensionRequest,
    ): Response

    @DELETE
    @Path("/current")
    fun end(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        request: PlatformSubscriptionTrialEndRequest,
    ): Response

    @POST
    @Path("/current/conversions")
    fun convert(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        request: PlatformSubscriptionTrialConversionRequest,
    ): Response
}
