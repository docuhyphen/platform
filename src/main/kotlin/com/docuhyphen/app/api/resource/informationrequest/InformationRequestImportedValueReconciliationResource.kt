package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestExternalSourceDtoMapper
import com.docuhyphen.app.api.model.informationrequest.ReconcileInformationRequestImportedValuesCommand
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestImportedValueService
import jakarta.inject.Inject
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-requests/{id}/imported-value-reconciliations")
@Produces(APPLICATION_JSON)
class InformationRequestImportedValueReconciliationResource @Inject constructor(
    private val importedValues: InformationRequestImportedValueService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @POST
    fun reconcile(
        @PathParam("id") id: String,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = InformationRequestCommandHttp.uuid(id, "information request id")
            val key = InformationRequestCommandHttp.idempotencyKey(idempotencyKey)
            val access = accessContextFactory.currentAuthenticated()
            val results = importedValues.reconcile(ReconcileInformationRequestImportedValuesCommand(requestId, access, key))
            Response.ok(results.map(InformationRequestExternalSourceDtoMapper::toDto).toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request imported value reconciliation failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestImportedValueReconciliationResource::class.java)
    }
}
