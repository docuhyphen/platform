package com.docuhyphen.app.api.resource.informationrequest.attestation

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.attestation.handler.InformationRequestAttestationRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.attestation.operations.InformationRequestNoAuthAttestationResourceOperations
import com.docuhyphen.app.api.resource.model.RecordInformationRequestAttestationRequest
import com.docuhyphen.app.api.service.informationrequest.attestation.InformationRequestSubmissionAttestationService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestNoAuthAttestationResource @Inject constructor(
    attestationService: InformationRequestSubmissionAttestationService,
    private val readAccessService: InformationRequestNoAuthReadAccessService,
) : InformationRequestNoAuthAttestationResourceOperations
{
    private val handler = InformationRequestAttestationRequestHandler(attestationService)

    override fun record(
        id: String,
        requirementId: String,
        request: RecordInformationRequestAttestationRequest,
        accessLinkToken: String?,
        ifMatch: String?,
        idempotencyKey: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                handler.record(
                    requestId,
                    InformationRequestCommandHttp.uuid(requirementId, "requirement id"),
                    request,
                    access,
                    ifMatch,
                    idempotencyKey,
                )
            }
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "No-auth Information Request attestation failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestNoAuthAttestationResource::class.java)
    }
}
