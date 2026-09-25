package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.SESSION_TOKEN_HEADER
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAmendmentQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAmendmentService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestNoAuthReadAccessService
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("no-auth/information-requests/{id}/amendments")
@Produces(APPLICATION_JSON)
class InformationRequestNoAuthAmendmentResource @Inject constructor(
    amendmentService: InformationRequestAmendmentService,
    queryService: InformationRequestAmendmentQueryService,
    private val readAccessService: InformationRequestNoAuthReadAccessService,
)
{
    private val endpoint = InformationRequestAmendmentEndpoint(amendmentService, queryService)

    @GET
    fun list(
        @PathParam("id") id: String,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                endpoint.list(requestId, access)
            }
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "No-auth Information Request amendment list failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestNoAuthAmendmentResource::class.java)
    }
}
