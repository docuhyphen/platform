package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.SESSION_TOKEN_HEADER
import com.docuhyphen.app.api.resource.model.RecordInformationRequestAttestationRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestNoAuthReadAccessService
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

@Path("no-auth/information-requests/{id}/requirements/{requirementId}/attestations")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestNoAuthAttestationResource @Inject constructor(
    attestationService: InformationRequestSubmissionAttestationService,
    private val readAccessService: InformationRequestNoAuthReadAccessService,
)
{
    private val endpoint = InformationRequestAttestationEndpoint(attestationService)

    @POST
    fun record(
        @PathParam("id") id: String,
        @PathParam("requirementId") requirementId: String,
        request: RecordInformationRequestAttestationRequest,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                endpoint.record(
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
