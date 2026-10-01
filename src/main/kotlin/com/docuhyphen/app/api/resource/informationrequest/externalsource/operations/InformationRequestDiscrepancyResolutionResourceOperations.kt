package com.docuhyphen.app.api.resource.informationrequest.externalsource.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.ResolveInformationRequestDiscrepancyRequest
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/imported-value-discrepancies/{discrepancyId}/resolutions")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestDiscrepancyResolutionResourceOperations
{
    @POST
    fun resolve(
        @PathParam("id") id: String,
        @PathParam("discrepancyId") discrepancyId: String,
        request: ResolveInformationRequestDiscrepancyRequest?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
}
