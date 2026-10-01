package com.docuhyphen.app.api.resource.informationrequest.externalsource

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestExternalSourceDtoMapper
import com.docuhyphen.app.api.model.informationrequest.externalsource.ResolveInformationRequestDiscrepancyCommand
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.externalsource.operations.InformationRequestDiscrepancyResolutionResourceOperations
import com.docuhyphen.app.api.resource.model.ResolveInformationRequestDiscrepancyRequest
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestImportedValueService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestDiscrepancyResolutionResource @Inject constructor(
    private val importedValues: InformationRequestImportedValueService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestDiscrepancyResolutionResourceOperations
{
    override fun resolve(
        id: String,
        discrepancyId: String,
        request: ResolveInformationRequestDiscrepancyRequest?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = InformationRequestCommandHttp.uuid(id, "information request id")
            val discrepancy = InformationRequestCommandHttp.uuid(discrepancyId, "discrepancy id")
            val body =
                request ?: throw InformationRequestCommandRequestException("A resolution states its outcome and reason")
            val key = InformationRequestCommandHttp.idempotencyKey(idempotencyKey)
            val access = accessContextFactory.currentAuthenticated()
            val view = importedValues.resolve(
                ResolveInformationRequestDiscrepancyCommand(
                    requestId,
                    discrepancy,
                    body.resolution,
                    body.reasonCode,
                    access,
                    key
                ),
            )
            Response.status(Response.Status.CREATED)
                .entity(InformationRequestExternalSourceDtoMapper.toDto(view, access.principal)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(
                logger,
                "Information Request discrepancy resolution failed",
                exception
            )
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestDiscrepancyResolutionResource::class.java)
    }
}
