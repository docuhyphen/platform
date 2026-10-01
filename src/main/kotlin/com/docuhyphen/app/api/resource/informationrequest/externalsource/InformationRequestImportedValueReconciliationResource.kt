package com.docuhyphen.app.api.resource.informationrequest.externalsource

import com.docuhyphen.app.api.model.InformationRequestExternalSourceDtoMapper
import com.docuhyphen.app.api.model.informationrequest.externalsource.ReconcileInformationRequestImportedValuesCommand
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.externalsource.operations.InformationRequestImportedValueReconciliationResourceOperations
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestImportedValueService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestImportedValueReconciliationResource @Inject constructor(
    private val importedValues: InformationRequestImportedValueService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestImportedValueReconciliationResourceOperations
{
    override fun reconcile(
        id: String,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = InformationRequestCommandHttp.uuid(id, "information request id")
            val key = InformationRequestCommandHttp.idempotencyKey(idempotencyKey)
            val access = accessContextFactory.currentAuthenticated()
            val results =
                importedValues.reconcile(ReconcileInformationRequestImportedValuesCommand(requestId, access, key))
            Response.ok(results.map(InformationRequestExternalSourceDtoMapper::toDto).toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(
                logger,
                "Information Request imported value reconciliation failed",
                exception
            )
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestImportedValueReconciliationResource::class.java)
    }
}
