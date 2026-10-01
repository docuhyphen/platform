package com.docuhyphen.app.api.resource.informationrequest.externalsource.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/imported-value-reconciliations")
@Produces(APPLICATION_JSON)
interface InformationRequestImportedValueReconciliationResourceOperations
{
    @POST
    fun reconcile(
        @PathParam("id") id: String,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
}
