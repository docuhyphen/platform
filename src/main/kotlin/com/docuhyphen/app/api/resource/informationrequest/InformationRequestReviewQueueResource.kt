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
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-request-reviews")
@Produces(APPLICATION_JSON)
class InformationRequestReviewQueueResource @Inject constructor(
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
    fun queue(): Response
    {
        return try
        {
            endpoint.queue(accessContextFactory.currentAuthenticated())
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request reviewer queue lookup failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestReviewQueueResource::class.java)
    }
}
