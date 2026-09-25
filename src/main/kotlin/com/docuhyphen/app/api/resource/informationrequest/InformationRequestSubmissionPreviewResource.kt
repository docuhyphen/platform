package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubmissionQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubmissionService
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-requests/{id}/submission-preview")
@Produces(APPLICATION_JSON)
class InformationRequestSubmissionPreviewResource @Inject constructor(
    submissionService: InformationRequestSubmissionService,
    queryService: InformationRequestSubmissionQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    private val endpoint = InformationRequestSubmissionEndpoint(submissionService, queryService)

    @GET
    fun preview(@PathParam("id") id: String, @QueryParam("stageKey") stageKey: String?): Response
    {
        return try
        {
            endpoint.preview(
                InformationRequestCommandHttp.uuid(id, "information request id"),
                stageKey,
                accessContextFactory.currentAuthenticated(),
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request submission preview failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestSubmissionPreviewResource::class.java)
    }
}
