package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestReviewAssignmentService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestReviewCommentService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestReviewCycleService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestReviewDecisionService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestReviewFindingService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestReviewQueryService
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-requests/{id}/review-results")
@Produces(APPLICATION_JSON)
class InformationRequestReviewResultResource @Inject constructor(
    assignments: InformationRequestReviewAssignmentService,
    decisions: InformationRequestReviewDecisionService,
    findings: InformationRequestReviewFindingService,
    comments: InformationRequestReviewCommentService,
    cycles: InformationRequestReviewCycleService,
    queries: InformationRequestReviewQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    private val endpoint = InformationRequestReviewEndpoint(assignments, decisions, findings, comments, cycles, queries)

    @GET
    fun results(@PathParam("id") id: String): Response
    {
        return try
        {
            endpoint.results(InformationRequestCommandHttp.uuid(id, "information request id"), accessContextFactory.currentAuthenticated())
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request review results lookup failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestReviewResultResource::class.java)
    }
}
