package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.RecordInformationRequestAttestationRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubmissionAttestationService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-requests/{id}/requirements/{requirementId}/attestations")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestAttestationResource @Inject constructor(
    attestationService: InformationRequestSubmissionAttestationService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    private val endpoint = InformationRequestAttestationEndpoint(attestationService)

    @POST
    fun record(
        @PathParam("id") id: String,
        @PathParam("requirementId") requirementId: String,
        request: RecordInformationRequestAttestationRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            endpoint.record(
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
