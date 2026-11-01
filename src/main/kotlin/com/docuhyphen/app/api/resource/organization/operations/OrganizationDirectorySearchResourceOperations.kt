package com.docuhyphen.app.api.resource.organization.operations

import com.docuhyphen.app.api.resource.RequestHeaders
import com.docuhyphen.app.api.resource.model.OrganizationDirectorySearchRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("organization-directory-searches")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface OrganizationDirectorySearchResourceOperations {
    @POST
    fun search(
        request: OrganizationDirectorySearchRequest,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response
}
