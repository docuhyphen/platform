package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.SESSION_TOKEN_HEADER
import com.docuhyphen.app.api.service.informationrequest.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubmissionQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubmissionService
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("no-auth/information-requests/{id}/submission-preview")
@Produces(APPLICATION_JSON)
class InformationRequestNoAuthSubmissionPreviewResource @Inject constructor(
    submissionService: InformationRequestSubmissionService,
    queryService: InformationRequestSubmissionQueryService,
    private val readAccessService: InformationRequestNoAuthReadAccessService,
)
{
    private val endpoint = InformationRequestSubmissionEndpoint(submissionService, queryService)

    @GET
    fun preview(
        @PathParam("id") id: String,
        @QueryParam("stageKey") stageKey: String?,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                endpoint.preview(requestId, stageKey, access)
            }
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "No-auth Information Request submission preview failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestNoAuthSubmissionPreviewResource::class.java)
    }
}
