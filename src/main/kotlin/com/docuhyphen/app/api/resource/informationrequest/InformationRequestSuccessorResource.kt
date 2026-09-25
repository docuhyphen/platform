package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestLineageDtoMapper
import com.docuhyphen.app.api.model.informationrequest.CreateInformationRequestSuccessorCommand
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.CreateInformationRequestSuccessorRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLineageQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSuccessorService
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

@Path("/information-requests/{id}/successors")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestSuccessorResource @Inject constructor(
    private val successorService: InformationRequestSuccessorService,
    private val lineageQueryService: InformationRequestLineageQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @GET
    fun lineage(@PathParam("id") id: String): Response
    {
        return try
        {
            val view = lineageQueryService.lineage(requestId(id), accessContextFactory.currentAuthenticated())
            Response.ok(InformationRequestLineageDtoMapper.toDto(view)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request lineage lookup failed", exception)
        }
    }

    @POST
    fun create(
        @PathParam("id") id: String,
        request: CreateInformationRequestSuccessorRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val result = successorService.create(
                CreateInformationRequestSuccessorCommand(
                    sourceRequestId = requestId(id),
                    kind = request.kind,
                    targetTemplateVersionId = request.targetTemplateVersionId,
                    sourcePackageId = request.sourcePackageId,
                    reasonCode = request.reasonCode?.trim()?.ifBlank { null },
                    access = accessContextFactory.currentAuthenticated(),
                    precondition = CommandPreconditionHeader.required(ifMatch),
                    idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
                ),
            )
            Response.status(Response.Status.CREATED)
                .entity(InformationRequestLineageDtoMapper.toDto(result))
                .header("ETag", result.successorETag)
                .build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request follow-up creation failed", exception)
        }
    }

    private fun requestId(raw: String) = InformationRequestCommandHttp.uuid(raw, "information request id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestSuccessorResource::class.java)
    }
}
