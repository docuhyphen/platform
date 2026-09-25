package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.SubmitInformationRequestPackageRequest
import com.docuhyphen.app.api.resource.model.WithdrawInformationRequestPackageRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubmissionQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubmissionService
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

@Path("/information-requests/{id}/submissions")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestSubmissionResource @Inject constructor(
    submissionService: InformationRequestSubmissionService,
    queryService: InformationRequestSubmissionQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    private val endpoint = InformationRequestSubmissionEndpoint(submissionService, queryService)

    @GET
    fun list(@PathParam("id") id: String): Response
    {
        return try
        {
            endpoint.list(requestId(id), accessContextFactory.currentAuthenticated())
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request submission list failed", exception)
        }
    }

    @GET
    @Path("/{packageId}")
    fun detail(@PathParam("id") id: String, @PathParam("packageId") packageId: String): Response
    {
        return try
        {
            endpoint.detail(requestId(id), packageId(packageId), accessContextFactory.currentAuthenticated())
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request submission lookup failed", exception)
        }
    }

    @POST
    fun submit(
        @PathParam("id") id: String,
        request: SubmitInformationRequestPackageRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            endpoint.submit(requestId(id), request?.stageKey, accessContextFactory.currentAuthenticated(), ifMatch, idempotencyKey)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request submission failed", exception)
        }
    }

    @POST
    @Path("/{packageId}/withdrawal")
    fun withdraw(
        @PathParam("id") id: String,
        @PathParam("packageId") packageId: String,
        request: WithdrawInformationRequestPackageRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            endpoint.withdraw(
                requestId(id),
                packageId(packageId),
                request?.reasonCode,
                accessContextFactory.currentAuthenticated(),
                ifMatch,
                idempotencyKey,
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request submission withdrawal failed", exception)
        }
    }

    private fun requestId(raw: String) = InformationRequestCommandHttp.uuid(raw, "information request id")

    private fun packageId(raw: String) = InformationRequestCommandHttp.uuid(raw, "submission package id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestSubmissionResource::class.java)
    }
}
