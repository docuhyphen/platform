package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.AmendInformationRequestRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAmendmentQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAmendmentService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-requests/{id}/amendments")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestAmendmentResource @Inject constructor(
    amendmentService: InformationRequestAmendmentService,
    queryService: InformationRequestAmendmentQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    private val endpoint = InformationRequestAmendmentEndpoint(amendmentService, queryService)

    @GET
    fun list(@PathParam("id") id: String): Response
    {
        return try
        {
            endpoint.list(requestId(id), accessContextFactory.currentAuthenticated())
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request amendment list failed", exception)
        }
    }

    @POST
    fun amend(
        @PathParam("id") id: String,
        request: AmendInformationRequestRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            endpoint.amend(requestId(id), request, accessContextFactory.currentAuthenticated(), ifMatch, idempotencyKey)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request amendment failed", exception)
        }
    }

    private fun requestId(raw: String) = InformationRequestCommandHttp.uuid(raw, "information request id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestAmendmentResource::class.java)
    }
}
