package com.docuhyphen.app.api.resource.informationrequest.attestation

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.attestation.handler.InformationRequestAttestationRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.attestation.operations.InformationRequestAttestationResourceOperations
import com.docuhyphen.app.api.resource.model.RecordInformationRequestAttestationRequest
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.attestation.InformationRequestSubmissionAttestationService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestAttestationResource @Inject constructor(
    attestationService: InformationRequestSubmissionAttestationService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestAttestationResourceOperations
{
    private val handler = InformationRequestAttestationRequestHandler(attestationService)

    override fun record(
        id: String,
        requirementId: String,
        request: RecordInformationRequestAttestationRequest,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.record(
                InformationRequestCommandHttp.uuid(id, "information request id"),
                InformationRequestCommandHttp.uuid(requirementId, "requirement id"),
                request,
                accessContextFactory.currentAuthenticated(),
                ifMatch,
                idempotencyKey,
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request attestation failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestAttestationResource::class.java)
    }
}
