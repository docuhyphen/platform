package com.docuhyphen.app.api.resource.organization.operations

import com.docuhyphen.app.api.model.dto.PlatformOrganizationStatusUpdateRequest
import com.docuhyphen.app.api.resource.RequestHeaders
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/platform/organizations")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface PlatformOrganizationResourceOperations {
    @GET
    fun list(
        @QueryParam("query") query: String?,
        @QueryParam("status") status: String?,
        @QueryParam("tierCode") tierCode: String?,
        @QueryParam("sort") sort: String?,
        @QueryParam("direction") direction: String?,
        @QueryParam("limit") limit: Int?,
        @QueryParam("offset") offset: Int?,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @GET
    @Path("/{organizationId}")
    fun get(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @PATCH
    @Path("/{organizationId}/status")
    fun updateStatus(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        payload: PlatformOrganizationStatusUpdateRequest,
    ): Response
}
