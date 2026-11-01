package com.docuhyphen.app.api.resource.organization.operations

import com.docuhyphen.app.api.model.dto.PlatformOrganizationFeatureEntitlementsUpdateRequest
import com.docuhyphen.app.api.resource.RequestHeaders
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/platform/organizations/{organizationId}/feature-entitlements")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface PlatformOrganizationFeatureEntitlementResourceOperations {
    @GET
    fun get(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @PUT
    fun replace(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        payload: PlatformOrganizationFeatureEntitlementsUpdateRequest,
    ): Response
}
